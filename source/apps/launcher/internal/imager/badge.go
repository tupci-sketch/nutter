package imager

import (
	"encoding/xml"
	"fmt"
	"image"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// Group badges are not stored as pictures. A group's badge is a short code
// naming a base shape and up to four symbols, each with a colour and a place on
// a three-by-three grid, and the picture is built from that on demand.

// BadgeSize is the width and height of a composed badge in pixels.
const BadgeSize = 39

// maxBadgeParts caps how many parts one code may carry, matching the editor's
// base plus four symbols. A longer code is a malformed one.
const maxBadgeParts = 5

// BadgePart is one decoded token of a badge code.
type BadgePart struct {
	// Base marks the backing shape, which is drawn first and fills the badge.
	Base bool
	// ID is the part number within its kind.
	ID int
	// Colour is the palette entry the part is drawn in.
	Colour int
	// Position is a cell of the three-by-three grid, 0 top-left to 8
	// bottom-right. Base parts and oversized symbols ignore it.
	Position int
}

// ParseBadgeCode decodes a group badge code such as "b03120s13181s01014".
//
// Each part is a letter and exactly five digits: two for the part id, two for
// the colour and one for the position. "b" selects a base shape, "s" a symbol,
// and "t" a symbol numbered a hundred higher, which is how the format reaches
// part ids above ninety-nine.
//
// A code that does not decode cleanly is rejected rather than half-drawn: a
// badge missing its symbols looks like a different group's badge, which is
// worse than showing none.
func ParseBadgeCode(code string) ([]BadgePart, error) {
	if code == "" {
		return nil, fmt.Errorf("empty badge code")
	}
	if len(code)%6 != 0 {
		return nil, fmt.Errorf("badge code %q is not a whole number of parts", code)
	}
	if len(code)/6 > maxBadgeParts {
		return nil, fmt.Errorf("badge code %q has more than %d parts", code, maxBadgeParts)
	}

	parts := make([]BadgePart, 0, len(code)/6)
	for i := 0; i < len(code); i += 6 {
		token := code[i : i+6]
		kind := token[0]
		digits := token[1:]

		for _, c := range digits {
			if c < '0' || c > '9' {
				return nil, fmt.Errorf("badge part %q is not a letter and five digits", token)
			}
		}

		id, _ := strconv.Atoi(digits[0:2])
		colour, _ := strconv.Atoi(digits[2:4])
		position, _ := strconv.Atoi(digits[4:5])

		switch kind {
		case 'b':
			parts = append(parts, BadgePart{Base: true, ID: id, Colour: colour, Position: position})
		case 's':
			parts = append(parts, BadgePart{ID: id, Colour: colour, Position: position})
		case 't':
			// "t" continues the numbering where two digits run out.
			parts = append(parts, BadgePart{ID: id + 100, Colour: colour, Position: position})
		default:
			return nil, fmt.Errorf("badge part %q has unknown kind %q", token, string(kind))
		}
	}
	return parts, nil
}

// ─── badge parts in the pack ────────────────────────────────────────────────

// BadgeParts is the pack's badge artwork: which sprite holds each part and, if
// the pack colours parts by tinting, the palette to tint them with.
type BadgeParts struct {
	// Bases and symbols map a part id to its sprite name.
	Bases   map[int]string
	Symbols map[int]string
	// Palette maps a colour id to 0xRRGGBB. Empty when the pack ships one
	// sprite per colour instead of tinting a single greyscale part.
	Palette map[int]uint32
}

type badgePartsXML struct {
	Colours []struct {
		ID    string `xml:"id,attr"`
		Value string `xml:",chardata"`
	} `xml:"color"`
	Bases []struct {
		ID  string `xml:"id,attr"`
		Lib string `xml:"lib,attr"`
	} `xml:"basepart"`
	Symbols []struct {
		ID  string `xml:"id,attr"`
		Lib string `xml:"lib,attr"`
	} `xml:"symbolpart"`
	Parts []struct {
		Type string `xml:"type,attr"`
		ID   string `xml:"id,attr"`
		Lib  string `xml:"lib,attr"`
	} `xml:"part"`
}

// LoadBadgeParts reads badgeparts.xml from a pack root.
//
// The file is optional. A pack without it can still serve badges whose parts
// follow the usual sprite naming, which is why a missing file is not an error.
func LoadBadgeParts(root string) (*BadgeParts, error) {
	parts := &BadgeParts{
		Bases:   map[int]string{},
		Symbols: map[int]string{},
		Palette: map[int]uint32{},
	}

	data, err := os.ReadFile(filepath.Join(root, "badgeparts.xml"))
	if os.IsNotExist(err) {
		return parts, nil
	}
	if err != nil {
		return nil, fmt.Errorf("read badgeparts: %w", err)
	}

	var parsed badgePartsXML
	if err := xml.Unmarshal(data, &parsed); err != nil {
		return nil, fmt.Errorf("parse badgeparts: %w", err)
	}

	for _, colour := range parsed.Colours {
		id, err := strconv.Atoi(strings.TrimSpace(colour.ID))
		if err != nil {
			continue
		}
		parts.Palette[id] = parseHexColour(colour.Value)
	}
	for _, base := range parsed.Bases {
		if id, err := strconv.Atoi(base.ID); err == nil && base.Lib != "" {
			parts.Bases[id] = base.Lib
		}
	}
	for _, symbol := range parsed.Symbols {
		if id, err := strconv.Atoi(symbol.ID); err == nil && symbol.Lib != "" {
			parts.Symbols[id] = symbol.Lib
		}
	}
	// Some packs describe both kinds through one element instead.
	for _, part := range parsed.Parts {
		id, err := strconv.Atoi(part.ID)
		if err != nil || part.Lib == "" {
			continue
		}
		if part.Type == "base" {
			parts.Bases[id] = part.Lib
		} else {
			parts.Symbols[id] = part.Lib
		}
	}
	return parts, nil
}

// badgeCandidate is one sprite name that might hold a part, and whether that
// sprite already carries the requested colour.
type badgeCandidate struct {
	name     string
	coloured bool
}

// spriteCandidates lists the sprites that could hold one badge part, best first.
//
// Packs differ in whether a part ships once per colour or once in greyscale to
// be tinted, so both are tried before giving up. The name declared in
// badgeparts.xml wins when the pack provides one.
func (b *BadgeParts) spriteCandidates(part BadgePart) []badgeCandidate {
	kind, declared := "badgepart", b.Symbols[part.ID]
	if part.Base {
		kind, declared = "badgepart_base", b.Bases[part.ID]
	}

	var names []badgeCandidate
	if declared != "" {
		names = append(names,
			badgeCandidate{fmt.Sprintf("%s_%d", declared, part.Colour), true},
			badgeCandidate{declared, false})
	}
	return append(names,
		badgeCandidate{fmt.Sprintf("%s_%d_%d", kind, part.ID, part.Colour), true},
		badgeCandidate{fmt.Sprintf("%s_%d", kind, part.ID), false})
}

// RenderBadge composes a group badge code into a PNG.
func (r *Renderer) RenderBadge(code string, badgeParts *BadgeParts) ([]byte, error) {
	parts, err := ParseBadgeCode(code)
	if err != nil {
		return nil, err
	}

	canvas := image.NewNRGBA(image.Rect(0, 0, BadgeSize, BadgeSize))
	drawn := 0

	for _, part := range parts {
		img, tinted, ok := r.badgePartImage(part, badgeParts)
		if !ok {
			continue
		}

		placement := Placement{Image: img}
		if !tinted {
			if rgb, has := badgeParts.Palette[part.Colour]; has {
				placement.Tint, placement.HasTint = rgb, true
			}
		}

		prepared := prepare(placement)
		at := badgePlacement(part, prepared.Bounds().Size())
		drawInto(canvas, prepared, at)
		drawn++
	}

	if drawn == 0 {
		return nil, fmt.Errorf("badge code %q has no parts in this pack", code)
	}
	return EncodePNG(canvas)
}

// badgePartImage finds the artwork for one part, reporting whether the sprite
// already carries the requested colour.
func (r *Renderer) badgePartImage(part BadgePart, badgeParts *BadgeParts) (image.Image, bool, bool) {
	for _, candidate := range badgeParts.spriteCandidates(part) {
		entry, _, ok := r.pack.Resolve(candidate.name)
		if !ok {
			continue
		}
		img, err := r.loadImage(entry.File)
		if err != nil {
			continue
		}
		return img, candidate.coloured, true
	}
	return nil, false, false
}

// badgePlacement puts a part in its grid cell.
//
// Positions run 0 to 8 across a three-by-three grid, and a part is centred in
// its cell. A part too large for a cell is centred on the badge instead, which
// is what the badge editor does with full-size shapes.
func badgePlacement(part BadgePart, size image.Point) image.Point {
	cell := BadgeSize / 3

	if part.Base || size.X > cell || size.Y > cell {
		return image.Pt((BadgeSize-size.X)/2, (BadgeSize-size.Y)/2)
	}

	position := part.Position
	if position < 0 || position > 8 {
		position = 4
	}
	column, row := position%3, position/3

	return image.Pt(
		column*cell+(cell-size.X)/2,
		row*cell+(cell-size.Y)/2,
	)
}

// drawInto composites one image onto the canvas at a point, clipped to it.
func drawInto(canvas *image.NRGBA, src image.Image, at image.Point) {
	target := image.Rectangle{Min: at, Max: at.Add(src.Bounds().Size())}
	drawOver(canvas, target.Intersect(canvas.Bounds()), src, at)
}
