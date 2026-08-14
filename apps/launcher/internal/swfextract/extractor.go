// Package swfextract parses SWF (Flash) files and extracts embedded PNG sprites.
// It handles FWS (uncompressed) and CWS (zlib-compressed) SWF files and
// extracts DefineBitsLossless2 (ARGB) and DefineBitsJPEG3 (JPEG+alpha) images,
// naming them via SymbolClass / ExportAssets tags.
package swfextract

import (
	"bytes"
	"compress/zlib"
	"encoding/binary"
	"encoding/json"
	"fmt"
	"image"
	"image/color"
	"image/jpeg"
	"image/png"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
)

// SWF tag type IDs we process.
const (
	tagEnd                 uint16 = 0
	tagDefineBitsJPEG2     uint16 = 21
	tagDefineBitsJPEG3     uint16 = 35
	tagDefineBitsLossless2 uint16 = 36
	tagExportAssets        uint16 = 56
	tagSymbolClass         uint16 = 76
	tagDefineBitsJPEG4     uint16 = 90
)

// SpriteEntry records a single extracted sprite in the manifest.
type SpriteEntry struct {
	File   string `json:"file"`
	Width  int    `json:"width"`
	Height int    `json:"height"`
}

// Manifest is the top-level output manifest written to manifest.json.
type Manifest struct {
	Sprites map[string]SpriteEntry `json:"sprites"`
}

// ExtractDir walks inputDir, runs Extract on every .swf file found,
// and writes all PNGs + a combined manifest.json to outputDir.
func ExtractDir(inputDir, outputDir string) (*Manifest, error) {
	if err := os.MkdirAll(outputDir, 0755); err != nil {
		return nil, err
	}

	manifest := &Manifest{Sprites: make(map[string]SpriteEntry)}

	err := filepath.WalkDir(inputDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		if strings.ToLower(filepath.Ext(path)) != ".swf" {
			return nil
		}
		m, err := Extract(path, outputDir)
		if err != nil {
			// Log but don't abort the whole walk for one bad SWF.
			fmt.Fprintf(os.Stderr, "warn: %s: %v\n", path, err)
			return nil
		}
		for name, entry := range m.Sprites {
			manifest.Sprites[name] = entry
		}
		return nil
	})
	if err != nil {
		return nil, err
	}

	if err := writeManifest(filepath.Join(outputDir, "manifest.json"), manifest); err != nil {
		return nil, err
	}
	return manifest, nil
}

// Extract parses a single SWF file and writes extracted sprites to outputDir.
func Extract(swfPath, outputDir string) (*Manifest, error) {
	raw, err := os.ReadFile(swfPath)
	if err != nil {
		return nil, err
	}
	if len(raw) < 8 {
		return nil, fmt.Errorf("file too small to be a SWF")
	}

	sig := string(raw[0:3])
	body, err := decompressBody(sig, raw)
	if err != nil {
		return nil, fmt.Errorf("decompress: %w", err)
	}

	images, names, err := parseTags(body)
	if err != nil {
		return nil, fmt.Errorf("parse: %w", err)
	}

	manifest := &Manifest{Sprites: make(map[string]SpriteEntry)}

	for charID, name := range names {
		img, ok := images[charID]
		if !ok {
			continue
		}
		safe := sanitizeName(name)
		filename := safe + ".png"
		destPath := filepath.Join(outputDir, filename)

		if err := writePNG(destPath, img); err != nil {
			fmt.Fprintf(os.Stderr, "warn: write %s: %v\n", filename, err)
			continue
		}
		b := img.Bounds()
		manifest.Sprites[name] = SpriteEntry{File: filename, Width: b.Dx(), Height: b.Dy()}
	}
	return manifest, nil
}

// ─── decompression ──────────────────────────────────────────────────────────

func decompressBody(sig string, raw []byte) ([]byte, error) {
	switch sig {
	case "FWS":
		// Uncompressed — body starts after the 8-byte header.
		return raw[8:], nil
	case "CWS":
		// zlib-compressed body.
		r, err := zlib.NewReader(bytes.NewReader(raw[8:]))
		if err != nil {
			return nil, err
		}
		defer r.Close()
		return io.ReadAll(r)
	case "ZWS":
		// LZMA — not common in Habbo packs; skip.
		return nil, fmt.Errorf("LZMA-compressed SWF (ZWS) not supported")
	default:
		return nil, fmt.Errorf("not a SWF: unknown signature %q", sig)
	}
}

// ─── tag parser ─────────────────────────────────────────────────────────────

func parseTags(body []byte) (map[uint16]image.Image, map[uint16]string, error) {
	br := &bodyReader{data: body}

	// Skip RECT (variable bit-packed), FrameRate (uint16), FrameCount (uint16).
	if err := br.skipRect(); err != nil {
		return nil, nil, fmt.Errorf("skip RECT: %w", err)
	}
	br.byteAlign()
	br.skipBytes(4)

	images := make(map[uint16]image.Image)
	names := make(map[uint16]string)

	for {
		tagType, tagData, err := br.readTag()
		if err != nil || tagType == tagEnd {
			break
		}
		switch tagType {
		case tagDefineBitsLossless2:
			id, img, err := parseLossless2(tagData)
			if err == nil {
				images[id] = img
			}
		case tagDefineBitsJPEG3, tagDefineBitsJPEG4:
			id, img, err := parseJPEG3(tagData)
			if err == nil {
				images[id] = img
			}
		case tagSymbolClass, tagExportAssets:
			parseSymbols(tagData, names)
		}
	}
	return images, names, nil
}

// ─── DefineBitsLossless2 (tag 36) ───────────────────────────────────────────

func parseLossless2(data []byte) (uint16, image.Image, error) {
	if len(data) < 7 {
		return 0, nil, fmt.Errorf("tag too short")
	}
	r := bytes.NewReader(data)

	var charID uint16
	var format uint8
	var w, h uint16
	binary.Read(r, binary.LittleEndian, &charID)
	binary.Read(r, binary.LittleEndian, &format)
	binary.Read(r, binary.LittleEndian, &w)
	binary.Read(r, binary.LittleEndian, &h)

	if format == 3 {
		var ctSize uint8
		binary.Read(r, binary.LittleEndian, &ctSize) // skip colormap size
	}

	compressed, _ := io.ReadAll(r)
	zr, err := zlib.NewReader(bytes.NewReader(compressed))
	if err != nil {
		return 0, nil, err
	}
	pixels, err := io.ReadAll(zr)
	zr.Close()
	if err != nil {
		return 0, nil, err
	}

	switch format {
	case 5: // 32-bit ARGB, pre-multiplied
		need := int(w) * int(h) * 4
		if len(pixels) < need {
			return 0, nil, fmt.Errorf("pixel data too short (%d < %d)", len(pixels), need)
		}
		img := image.NewNRGBA(image.Rect(0, 0, int(w), int(h)))
		for y := 0; y < int(h); y++ {
			for x := 0; x < int(w); x++ {
				i := (y*int(w) + x) * 4
				a, rv, gv, bv := pixels[i], pixels[i+1], pixels[i+2], pixels[i+3]
				if a > 0 && a < 255 {
					rv = clamp(int(rv) * 255 / int(a))
					gv = clamp(int(gv) * 255 / int(a))
					bv = clamp(int(bv) * 255 / int(a))
				}
				img.SetNRGBA(x, y, color.NRGBA{R: rv, G: gv, B: bv, A: a})
			}
		}
		return charID, img, nil

	case 4: // 15-bit RGB (no alpha, 2 bytes per pixel)
		need := int(w) * int(h) * 2
		if len(pixels) < need {
			return 0, nil, fmt.Errorf("pixel data too short")
		}
		img := image.NewNRGBA(image.Rect(0, 0, int(w), int(h)))
		for y := 0; y < int(h); y++ {
			for x := 0; x < int(w); x++ {
				i := (y*int(w) + x) * 2
				p := binary.BigEndian.Uint16(pixels[i:])
				r5 := uint8((p >> 10) & 0x1F)
				g5 := uint8((p >> 5) & 0x1F)
				b5 := uint8(p & 0x1F)
				img.SetNRGBA(x, y, color.NRGBA{
					R: r5<<3 | r5>>2,
					G: g5<<3 | g5>>2,
					B: b5<<3 | b5>>2,
					A: 255,
				})
			}
		}
		return charID, img, nil

	default:
		return 0, nil, fmt.Errorf("unsupported bitmap format %d", format)
	}
}

// ─── DefineBitsJPEG3 (tag 35) ───────────────────────────────────────────────

func parseJPEG3(data []byte) (uint16, image.Image, error) {
	if len(data) < 6 {
		return 0, nil, fmt.Errorf("tag too short")
	}
	r := bytes.NewReader(data)

	var charID uint16
	var alphaOffset uint32
	binary.Read(r, binary.LittleEndian, &charID)
	binary.Read(r, binary.LittleEndian, &alphaOffset)

	jpegData := make([]byte, alphaOffset)
	if _, err := io.ReadFull(r, jpegData); err != nil {
		return 0, nil, err
	}

	// Some SWFs prepend an erroneous EOI+SOI pair; strip it.
	if len(jpegData) >= 4 && jpegData[0] == 0xFF && jpegData[1] == 0xD9 &&
		jpegData[2] == 0xFF && jpegData[3] == 0xD8 {
		jpegData = jpegData[4:]
	}

	jpegImg, err := jpeg.Decode(bytes.NewReader(jpegData))
	if err != nil {
		return 0, nil, fmt.Errorf("jpeg decode: %w", err)
	}

	// Alpha channel — zlib-compressed, one byte per pixel.
	var alphaBytes []byte
	if alphaZlib, _ := io.ReadAll(r); len(alphaZlib) > 0 {
		zr, err := zlib.NewReader(bytes.NewReader(alphaZlib))
		if err == nil {
			alphaBytes, _ = io.ReadAll(zr)
			zr.Close()
		}
	}

	b := jpegImg.Bounds()
	out := image.NewNRGBA(b)
	for y := b.Min.Y; y < b.Max.Y; y++ {
		for x := b.Min.X; x < b.Max.X; x++ {
			rc, gc, bc, _ := jpegImg.At(x, y).RGBA()
			a := uint8(255)
			if alphaBytes != nil {
				idx := (y-b.Min.Y)*b.Dx() + (x - b.Min.X)
				if idx < len(alphaBytes) {
					a = alphaBytes[idx]
				}
			}
			out.SetNRGBA(x, y, color.NRGBA{R: uint8(rc >> 8), G: uint8(gc >> 8), B: uint8(bc >> 8), A: a})
		}
	}
	return charID, out, nil
}

// ─── SymbolClass / ExportAssets (tags 76 / 56) ──────────────────────────────

func parseSymbols(data []byte, names map[uint16]string) {
	if len(data) < 2 {
		return
	}
	r := bytes.NewReader(data)
	var count uint16
	binary.Read(r, binary.LittleEndian, &count)
	for i := 0; i < int(count); i++ {
		var id uint16
		if err := binary.Read(r, binary.LittleEndian, &id); err != nil {
			break
		}
		name, err := readCString(r)
		if err != nil {
			break
		}
		names[id] = name
	}
}

// ─── body reader ────────────────────────────────────────────────────────────

type bodyReader struct {
	data   []byte
	pos    int
	bitBuf byte
	bitPos int // remaining bits in bitBuf (0 = empty)
}

func (b *bodyReader) readBits(n int) (uint32, error) {
	var v uint32
	for i := 0; i < n; i++ {
		if b.bitPos == 0 {
			if b.pos >= len(b.data) {
				return 0, io.EOF
			}
			b.bitBuf = b.data[b.pos]
			b.pos++
			b.bitPos = 8
		}
		v <<= 1
		if b.bitBuf&0x80 != 0 {
			v |= 1
		}
		b.bitBuf <<= 1
		b.bitPos--
	}
	return v, nil
}

func (b *bodyReader) byteAlign() { b.bitPos = 0 }

func (b *bodyReader) skipRect() error {
	nbits, err := b.readBits(5)
	if err != nil {
		return err
	}
	for i := 0; i < 4; i++ {
		if _, err := b.readBits(int(nbits)); err != nil {
			return err
		}
	}
	return nil
}

func (b *bodyReader) skipBytes(n int) { b.pos += n; b.bitPos = 0 }

func (b *bodyReader) readTag() (uint16, []byte, error) {
	if b.pos+2 > len(b.data) {
		return tagEnd, nil, io.EOF
	}
	header := binary.LittleEndian.Uint16(b.data[b.pos:])
	b.pos += 2

	tagType := header >> 6
	tagLen := uint32(header & 0x3F)
	if tagLen == 0x3F {
		if b.pos+4 > len(b.data) {
			return tagEnd, nil, io.EOF
		}
		tagLen = binary.LittleEndian.Uint32(b.data[b.pos:])
		b.pos += 4
	}

	end := b.pos + int(tagLen)
	if end > len(b.data) {
		end = len(b.data)
	}
	data := b.data[b.pos:end]
	b.pos = end
	return tagType, data, nil
}

// ─── helpers ────────────────────────────────────────────────────────────────

func readCString(r *bytes.Reader) (string, error) {
	var buf []byte
	for {
		c, err := r.ReadByte()
		if err != nil {
			return string(buf), err
		}
		if c == 0 {
			return string(buf), nil
		}
		buf = append(buf, c)
	}
}

func sanitizeName(s string) string {
	s = strings.ReplaceAll(s, "/", "_")
	s = strings.ReplaceAll(s, "\\", "_")
	s = strings.ReplaceAll(s, ":", "_")
	return s
}

func clamp(v int) uint8 {
	if v < 0 {
		return 0
	}
	if v > 255 {
		return 255
	}
	return uint8(v)
}

func writePNG(path string, img image.Image) error {
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	defer f.Close()
	return png.Encode(f, img)
}

func writeManifest(path string, m *Manifest) error {
	f, err := os.Create(path)
	if err != nil {
		return err
	}
	defer f.Close()
	enc := json.NewEncoder(f)
	enc.SetIndent("", "  ")
	return enc.Encode(m)
}
