package swfextract

import (
	"bytes"
	"compress/zlib"
	"encoding/binary"
	"encoding/json"
	"image"
	"image/color"
	"os"
	"path/filepath"
	"testing"
)

// These tests build SWF files byte by byte rather than checking in a binary
// fixture, so what each field means stays readable and a change in the parser
// fails against a file whose structure is visible in the test.

// buildSWF assembles an uncompressed SWF from the given tag bodies.
func buildSWF(tags []taggedBody) []byte {
	var body bytes.Buffer

	// RECT with nbits = 0, so the whole frame size is five bits of zero,
	// followed by frame rate and frame count.
	body.WriteByte(0x00)
	body.Write([]byte{0x00, 0x0C}) // frame rate 12
	body.Write([]byte{0x01, 0x00}) // frame count 1

	for _, t := range tags {
		writeTag(&body, t.code, t.data)
	}
	writeTag(&body, tagEnd, nil)

	// Header: signature, version, file length, then the body.
	var out bytes.Buffer
	out.WriteString("FWS")
	out.WriteByte(10)
	_ = binary.Write(&out, binary.LittleEndian, uint32(8+body.Len()))
	out.Write(body.Bytes())
	return out.Bytes()
}

type taggedBody struct {
	code uint16
	data []byte
}

// writeTag emits a tag header, using the long form when the body does not fit
// the six-bit short length field.
func writeTag(w *bytes.Buffer, code uint16, data []byte) {
	if len(data) < 0x3F {
		_ = binary.Write(w, binary.LittleEndian, uint16(code<<6|uint16(len(data))))
	} else {
		_ = binary.Write(w, binary.LittleEndian, uint16(code<<6|0x3F))
		_ = binary.Write(w, binary.LittleEndian, uint32(len(data)))
	}
	w.Write(data)
}

// losslessTag builds a DefineBitsLossless2 body holding a solid colour image.
func losslessTag(charID uint16, w, h int, c color.NRGBA) []byte {
	var raw bytes.Buffer
	for i := 0; i < w*h; i++ {
		// Stored as ARGB with the colour pre-multiplied by alpha.
		raw.WriteByte(c.A)
		raw.WriteByte(uint8(int(c.R) * int(c.A) / 255))
		raw.WriteByte(uint8(int(c.G) * int(c.A) / 255))
		raw.WriteByte(uint8(int(c.B) * int(c.A) / 255))
	}

	var deflated bytes.Buffer
	zw := zlib.NewWriter(&deflated)
	_, _ = zw.Write(raw.Bytes())
	_ = zw.Close()

	var out bytes.Buffer
	_ = binary.Write(&out, binary.LittleEndian, charID)
	out.WriteByte(5) // format 5: 32-bit ARGB
	_ = binary.Write(&out, binary.LittleEndian, uint16(w))
	_ = binary.Write(&out, binary.LittleEndian, uint16(h))
	out.Write(deflated.Bytes())
	return out.Bytes()
}

// symbolTag builds a SymbolClass body naming character ids.
func symbolTag(pairs map[uint16]string) []byte {
	var out bytes.Buffer
	_ = binary.Write(&out, binary.LittleEndian, uint16(len(pairs)))
	for id, name := range pairs {
		_ = binary.Write(&out, binary.LittleEndian, id)
		out.WriteString(name)
		out.WriteByte(0)
	}
	return out.Bytes()
}

// binaryDataTag builds a DefineBinaryData body wrapping the given payload.
func binaryDataTag(charID uint16, payload string) []byte {
	var out bytes.Buffer
	_ = binary.Write(&out, binary.LittleEndian, charID)
	_ = binary.Write(&out, binary.LittleEndian, uint32(0)) // reserved
	out.WriteString(payload)
	return out.Bytes()
}

func writeSWF(t *testing.T, dir, name string, data []byte) string {
	t.Helper()
	path := filepath.Join(dir, name)
	if err := os.WriteFile(path, data, 0o644); err != nil {
		t.Fatalf("write swf: %v", err)
	}
	return path
}

// ─── tests ──────────────────────────────────────────────────────────────────

func TestExtractsNamedSprite(t *testing.T) {
	dir := t.TempDir()
	swf := buildSWF([]taggedBody{
		{tagDefineBitsLossless2, losslessTag(1, 4, 3, color.NRGBA{R: 200, G: 100, B: 50, A: 255})},
		{tagSymbolClass, symbolTag(map[uint16]string{1: "throne_64_a_0_0"})},
	})
	path := writeSWF(t, dir, "throne.swf", swf)

	out := t.TempDir()
	m, err := Extract(path, out)
	if err != nil {
		t.Fatalf("extract: %v", err)
	}

	entry, ok := m.Sprites["throne_64_a_0_0"]
	if !ok {
		t.Fatalf("sprite not extracted; got %v", m.Sprites)
	}
	if entry.Width != 4 || entry.Height != 3 {
		t.Errorf("dimensions = %dx%d, want 4x3", entry.Width, entry.Height)
	}
	if _, err := os.Stat(filepath.Join(out, entry.File)); err != nil {
		t.Errorf("png not written: %v", err)
	}
}

func TestUnpremultipliesAlpha(t *testing.T) {
	dir := t.TempDir()
	// A half-transparent bright red: stored pre-multiplied, it must come back
	// out at full brightness with the alpha preserved.
	swf := buildSWF([]taggedBody{
		{tagDefineBitsLossless2, losslessTag(1, 1, 1, color.NRGBA{R: 255, G: 0, B: 0, A: 128})},
		{tagSymbolClass, symbolTag(map[uint16]string{1: "half_red"})},
	})
	path := writeSWF(t, dir, "half.swf", swf)

	out := t.TempDir()
	m, err := Extract(path, out)
	if err != nil {
		t.Fatalf("extract: %v", err)
	}

	f, err := os.Open(filepath.Join(out, m.Sprites["half_red"].File))
	if err != nil {
		t.Fatalf("open png: %v", err)
	}
	defer f.Close()

	img, _, err := image.Decode(f)
	if err != nil {
		t.Fatalf("decode png: %v", err)
	}
	r, g, b, a := img.At(0, 0).RGBA()
	// RGBA() returns alpha-premultiplied 16-bit values, so compare the ratio.
	if a == 0 {
		t.Fatal("alpha lost")
	}
	red := int(r * 0xFFFF / a >> 8)
	if red < 250 {
		t.Errorf("red channel = %d, want ~255 after un-premultiplying", red)
	}
	if g != 0 || b != 0 {
		t.Errorf("green/blue = %d/%d, want 0", g, b)
	}
	if got := int(a >> 8); got < 125 || got > 131 {
		t.Errorf("alpha = %d, want ~128", got)
	}
}

func TestReadsOffsetsFromAssetDescriptor(t *testing.T) {
	dir := t.TempDir()
	descriptor := `<assets>
	  <asset name="throne_64_a_0_0" x="27" y="52"/>
	</assets>`
	swf := buildSWF([]taggedBody{
		{tagDefineBitsLossless2, losslessTag(1, 2, 2, color.NRGBA{R: 1, G: 2, B: 3, A: 255})},
		{tagSymbolClass, symbolTag(map[uint16]string{1: "throne_64_a_0_0"})},
		{tagDefineBinaryData, binaryDataTag(99, descriptor)},
	})
	path := writeSWF(t, dir, "throne.swf", swf)

	m, err := Extract(path, t.TempDir())
	if err != nil {
		t.Fatalf("extract: %v", err)
	}

	entry := m.Sprites["throne_64_a_0_0"]
	if entry.OffsetX != 27 || entry.OffsetY != 52 {
		t.Errorf("offset = (%d,%d), want (27,52) — without this the sprite draws off its tile",
			entry.OffsetX, entry.OffsetY)
	}
}

func TestRecordsMirroredDirectionsAsAliases(t *testing.T) {
	dir := t.TempDir()
	// Direction 4 has no image of its own: it mirrors direction 0.
	descriptor := `<assets>
	  <asset name="chair_64_a_0_0" x="10" y="20"/>
	  <asset name="chair_64_a_4_0" source="chair_64_a_0_0" flipH="1" x="12" y="20"/>
	</assets>`
	swf := buildSWF([]taggedBody{
		{tagDefineBitsLossless2, losslessTag(1, 2, 2, color.NRGBA{R: 9, G: 9, B: 9, A: 255})},
		{tagSymbolClass, symbolTag(map[uint16]string{1: "chair_64_a_0_0"})},
		{tagDefineBinaryData, binaryDataTag(99, descriptor)},
	})
	path := writeSWF(t, dir, "chair.swf", swf)

	m, err := Extract(path, t.TempDir())
	if err != nil {
		t.Fatalf("extract: %v", err)
	}

	alias, ok := m.Sprites["chair_64_a_4_0"]
	if !ok {
		t.Fatal("mirrored direction missing; the client would find no asset for it")
	}
	if alias.Source != "chair_64_a_0_0" {
		t.Errorf("source = %q, want chair_64_a_0_0", alias.Source)
	}
	if !alias.FlipH {
		t.Error("flipH not recorded, so the mirror would render facing the wrong way")
	}
	if alias.File != "" {
		t.Errorf("alias should carry no file of its own, got %q", alias.File)
	}
	if alias.OffsetX != 12 {
		t.Errorf("alias offset = %d, want its own 12", alias.OffsetX)
	}
}

func TestIgnoresNonAssetBinaryData(t *testing.T) {
	dir := t.TempDir()
	// Packs also embed a library manifest, which carries no offsets.
	manifest := `<manifest><library name="chair" version="1.0"/></manifest>`
	swf := buildSWF([]taggedBody{
		{tagDefineBitsLossless2, losslessTag(1, 2, 2, color.NRGBA{A: 255})},
		{tagSymbolClass, symbolTag(map[uint16]string{1: "chair_64_a_0_0"})},
		{tagDefineBinaryData, binaryDataTag(99, manifest)},
	})
	path := writeSWF(t, dir, "chair.swf", swf)

	m, err := Extract(path, t.TempDir())
	if err != nil {
		t.Fatalf("extract should tolerate a non-asset payload: %v", err)
	}
	if len(m.Sprites) != 1 {
		t.Errorf("sprites = %d, want 1", len(m.Sprites))
	}
}

func TestExtractDirWritesCombinedManifest(t *testing.T) {
	in := t.TempDir()
	writeSWF(t, in, "a.swf", buildSWF([]taggedBody{
		{tagDefineBitsLossless2, losslessTag(1, 2, 2, color.NRGBA{A: 255})},
		{tagSymbolClass, symbolTag(map[uint16]string{1: "sofa_64_a_0_0"})},
	}))
	writeSWF(t, in, "b.swf", buildSWF([]taggedBody{
		{tagDefineBitsLossless2, losslessTag(1, 2, 2, color.NRGBA{A: 255})},
		{tagSymbolClass, symbolTag(map[uint16]string{1: "lamp_64_a_0_0"})},
	}))

	out := t.TempDir()
	m, err := ExtractDir(in, out)
	if err != nil {
		t.Fatalf("extract dir: %v", err)
	}
	if len(m.Sprites) != 2 {
		t.Errorf("sprites = %d, want 2", len(m.Sprites))
	}

	data, err := os.ReadFile(filepath.Join(out, "manifest.json"))
	if err != nil {
		t.Fatalf("manifest not written: %v", err)
	}
	var written Manifest
	if err := json.Unmarshal(data, &written); err != nil {
		t.Fatalf("manifest is not valid JSON: %v", err)
	}
	for _, want := range []string{"sofa_64_a_0_0", "lamp_64_a_0_0"} {
		if _, ok := written.Sprites[want]; !ok {
			t.Errorf("manifest missing %s", want)
		}
	}
}

func TestRejectsNonSWF(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "not.swf")
	if err := os.WriteFile(path, []byte("this is not a swf at all"), 0o644); err != nil {
		t.Fatal(err)
	}
	if _, err := Extract(path, t.TempDir()); err == nil {
		t.Error("expected an error for a file that is not a SWF")
	}
}

func TestHandlesCompressedSWF(t *testing.T) {
	dir := t.TempDir()
	plain := buildSWF([]taggedBody{
		{tagDefineBitsLossless2, losslessTag(1, 2, 2, color.NRGBA{A: 255})},
		{tagSymbolClass, symbolTag(map[uint16]string{1: "rug_64_a_0_0"})},
	})

	// Re-wrap the same body as CWS, which is how packs actually ship.
	var compressed bytes.Buffer
	compressed.WriteString("CWS")
	compressed.WriteByte(10)
	_ = binary.Write(&compressed, binary.LittleEndian, uint32(len(plain)))
	zw := zlib.NewWriter(&compressed)
	_, _ = zw.Write(plain[8:])
	_ = zw.Close()

	path := writeSWF(t, dir, "rug.swf", compressed.Bytes())
	m, err := Extract(path, t.TempDir())
	if err != nil {
		t.Fatalf("extract compressed: %v", err)
	}
	if _, ok := m.Sprites["rug_64_a_0_0"]; !ok {
		t.Error("sprite missing from compressed SWF")
	}
}
