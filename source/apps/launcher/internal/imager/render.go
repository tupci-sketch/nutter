package imager

import (
	"bytes"
	"fmt"
	"image"
	"image/color"
	"image/draw"
	"image/png"
	"os"
	"path/filepath"
	"strings"
	"sync"

	_ "image/gif"
	_ "image/jpeg"
)

// Rendering composites the layers a composition produced into a single image.
//
// The awkward parts are all here: sprites are positioned by an offset against a
// shared origin rather than by their own corners, mirrored directions reflect
// about that same origin, and colourable parts arrive greyscale and have to be
// multiplied by a palette colour.

// Placement is one image to draw, already resolved to pixels.
type Placement struct {
	Image image.Image
	// OffsetX and OffsetY position the image against the origin. The drawn
	// pixels start at (-OffsetX, -OffsetY).
	OffsetX int
	OffsetY int
	// Flip mirrors the image about the origin's vertical axis.
	Flip bool
	// Tint multiplies the image by this colour when HasTint is set.
	Tint    uint32
	HasTint bool
}

// Renderer draws compositions, holding a cache of decoded sprite images.
//
// Decoding a PNG per request would make a busy profile page slower than the
// hotel it belongs to, and the same few hundred part images serve every avatar
// on the site, so they are decoded once and kept.
type Renderer struct {
	pack *Pack

	mu     sync.RWMutex
	images map[string]image.Image
}

// NewRenderer prepares a renderer over one asset pack.
func NewRenderer(pack *Pack) *Renderer {
	return &Renderer{pack: pack, images: map[string]image.Image{}}
}

// Pack exposes the pack being rendered from.
func (r *Renderer) Pack() *Pack { return r.pack }

// RenderAvatar draws a figure and encodes it as a PNG.
func (r *Renderer) RenderAvatar(req Request) ([]byte, error) {
	layers := r.pack.Compose(req)
	if len(layers) == 0 {
		return nil, fmt.Errorf("figure %q has no drawable parts in this pack", req.Figure)
	}

	placements := make([]Placement, 0, len(layers))
	for _, layer := range layers {
		entry, packFlip, ok := r.pack.Resolve(layer.Sprite)
		if !ok {
			// A pack missing one hat should cost the hat, not the avatar.
			continue
		}
		img, err := r.loadImage(entry.File)
		if err != nil {
			continue
		}
		placements = append(placements, Placement{
			Image:   img,
			OffsetX: entry.OffsetX,
			OffsetY: entry.OffsetY,
			// Mirroring twice returns to the original orientation.
			Flip:    layer.Flip != packFlip,
			Tint:    layer.Tint,
			HasTint: layer.HasTint,
		})
	}

	if len(placements) == 0 {
		return nil, fmt.Errorf("no sprites found for figure %q", req.Figure)
	}
	return EncodePNG(Composite(placements))
}

// loadImage decodes a sprite file relative to the pack root, caching the result.
func (r *Renderer) loadImage(file string) (image.Image, error) {
	r.mu.RLock()
	cached, ok := r.images[file]
	r.mu.RUnlock()
	if ok {
		return cached, nil
	}

	// A pack is untrusted input, so a sprite path must not be able to reach
	// outside the pack directory.
	path, err := safeJoin(r.pack.Root, file)
	if err != nil {
		return nil, err
	}

	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read sprite %s: %w", file, err)
	}
	img, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("decode sprite %s: %w", file, err)
	}

	r.mu.Lock()
	r.images[file] = img
	r.mu.Unlock()
	return img, nil
}

// safeJoin resolves a pack-relative path, refusing anything that escapes the
// pack root.
func safeJoin(root, rel string) (string, error) {
	if filepath.IsAbs(rel) {
		return "", fmt.Errorf("sprite path %q must be relative to the pack", rel)
	}
	joined := filepath.Join(root, filepath.FromSlash(rel))
	cleanRoot := filepath.Clean(root)
	if joined != cleanRoot && !strings.HasPrefix(joined, cleanRoot+string(filepath.Separator)) {
		return "", fmt.Errorf("sprite path %q leaves the pack directory", rel)
	}
	return joined, nil
}

// Composite draws placements onto a canvas just large enough to hold them.
//
// The canvas is sized from the placements rather than fixed, so a head-only
// render is a small image instead of a full-body one padded with transparency.
func Composite(placements []Placement) *image.NRGBA {
	if len(placements) == 0 {
		return image.NewNRGBA(image.Rect(0, 0, 1, 1))
	}

	bounds := image.Rectangle{}
	rects := make([]image.Rectangle, len(placements))
	for i, p := range placements {
		rects[i] = placedBounds(p)
		if i == 0 {
			bounds = rects[i]
			continue
		}
		bounds = bounds.Union(rects[i])
	}

	canvas := image.NewNRGBA(image.Rect(0, 0, bounds.Dx(), bounds.Dy()))
	for i, p := range placements {
		prepared := prepare(p)
		target := rects[i].Sub(bounds.Min)
		draw.Draw(canvas, target, prepared, prepared.Bounds().Min, draw.Over)
	}
	return canvas
}

// placedBounds is where a placement lands in origin-relative coordinates.
//
// Unmirrored, pixel column i sits at -OffsetX+i. Mirrored, it reflects about
// the origin to OffsetX-i, so the image runs leftward from OffsetX.
func placedBounds(p Placement) image.Rectangle {
	size := p.Image.Bounds().Size()
	if p.Flip {
		return image.Rect(p.OffsetX-size.X+1, -p.OffsetY, p.OffsetX+1, -p.OffsetY+size.Y)
	}
	return image.Rect(-p.OffsetX, -p.OffsetY, -p.OffsetX+size.X, -p.OffsetY+size.Y)
}

// prepare applies tint and mirroring, returning an image ready to draw.
func prepare(p Placement) image.Image {
	if !p.HasTint && !p.Flip {
		return p.Image
	}

	src := p.Image
	b := src.Bounds()
	out := image.NewNRGBA(image.Rect(0, 0, b.Dx(), b.Dy()))

	tr, tg, tb := uint32(0xFF), uint32(0xFF), uint32(0xFF)
	if p.HasTint {
		tr = (p.Tint >> 16) & 0xFF
		tg = (p.Tint >> 8) & 0xFF
		tb = p.Tint & 0xFF
	}

	for y := 0; y < b.Dy(); y++ {
		for x := 0; x < b.Dx(); x++ {
			sx := b.Min.X + x
			if p.Flip {
				sx = b.Max.X - 1 - x
			}
			r, g, bl, a := src.At(sx, b.Min.Y+y).RGBA()
			if a == 0 {
				continue
			}
			// RGBA() reports alpha-premultiplied 16-bit values; recover the
			// straight colour before tinting so a tint does not darken with
			// transparency.
			out.SetNRGBA(x, y, color.NRGBA{
				R: uint8(scale(r, a) * tr / 0xFF),
				G: uint8(scale(g, a) * tg / 0xFF),
				B: uint8(scale(bl, a) * tb / 0xFF),
				A: uint8(a >> 8),
			})
		}
	}
	return out
}

// scale converts one premultiplied 16-bit channel to a straight 8-bit one.
func scale(channel, alpha uint32) uint32 {
	if alpha == 0 {
		return 0
	}
	value := channel * 0xFF / alpha
	if value > 0xFF {
		return 0xFF
	}
	return value
}

// EncodePNG writes an image out as PNG bytes.
func EncodePNG(img image.Image) ([]byte, error) {
	var buf bytes.Buffer
	if err := png.Encode(&buf, img); err != nil {
		return nil, fmt.Errorf("encode png: %w", err)
	}
	return buf.Bytes(), nil
}

// drawOver composites src into the given region of the canvas, where src's
// top-left corner sits at origin in canvas coordinates.
func drawOver(canvas *image.NRGBA, region image.Rectangle, src image.Image, origin image.Point) {
	if region.Empty() {
		return
	}
	offset := src.Bounds().Min.Sub(origin)
	draw.Draw(canvas, region, src, region.Min.Add(offset), draw.Over)
}

// decodePNG reads PNG bytes back into an image.
func decodePNG(data []byte) (image.Image, error) {
	img, err := png.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("decode png: %w", err)
	}
	return img, nil
}
