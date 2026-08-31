// Package imager renders avatar figures and group badges to PNG from an
// installed asset pack.
//
// A hotel that cannot draw its own avatars ends up pointing profile pictures at
// somebody else's server, which breaks the moment that server changes or goes
// away. This renders from the same pack the client uses, so the pictures on the
// website are the pack the hotel actually runs.
package imager

import (
	"encoding/json"
	"encoding/xml"
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"strings"
)

// SpriteEntry mirrors one entry of the manifest written during extraction.
type SpriteEntry struct {
	File    string `json:"file,omitempty"`
	Width   int    `json:"width,omitempty"`
	Height  int    `json:"height,omitempty"`
	OffsetX int    `json:"offsetX,omitempty"`
	OffsetY int    `json:"offsetY,omitempty"`
	Source  string `json:"source,omitempty"`
	FlipH   bool   `json:"flipH,omitempty"`
}

type manifestFile struct {
	Sprites map[string]SpriteEntry `json:"sprites"`
}

// Colour is one entry of a palette.
type Colour struct {
	ID  string
	RGB uint32 // 0xRRGGBB
}

// Part is one drawable piece of a set.
type Part struct {
	ID          string
	Type        string
	Index       int
	Colourable  bool
	ColourIndex int
}

// Set is one selectable option, such as a particular hairstyle.
type Set struct {
	ID    string
	Parts []Part
}

// SetType groups every option for one slot and names its palette.
type SetType struct {
	Type      string
	PaletteID string
	Sets      map[string]Set
}

// Pack is everything the renderer needs for one visual era.
type Pack struct {
	Root     string
	Sprites  map[string]SpriteEntry
	Palettes map[string]map[string]Colour
	SetTypes map[string]SetType
	// libraries maps "type:partID" to the sprite library holding it.
	libraries map[string]string
}

// ─── XML shapes ─────────────────────────────────────────────────────────────

type figureDataXML struct {
	Colors struct {
		Palettes []struct {
			ID     string `xml:"id,attr"`
			Colors []struct {
				ID    string `xml:"id,attr"`
				Value string `xml:",chardata"`
			} `xml:"color"`
		} `xml:"palette"`
	} `xml:"colors"`
	Sets struct {
		SetTypes []struct {
			Type      string `xml:"type,attr"`
			PaletteID string `xml:"paletteid,attr"`
			Sets      []struct {
				ID    string `xml:"id,attr"`
				Parts []struct {
					ID          string `xml:"id,attr"`
					Type        string `xml:"type,attr"`
					Index       int    `xml:"index,attr"`
					Colorable   string `xml:"colorable,attr"`
					ColourIndex string `xml:"colorindex,attr"`
				} `xml:"part"`
			} `xml:"set"`
		} `xml:"settype"`
	} `xml:"sets"`
}

type figureMapXML struct {
	Libs []struct {
		ID    string `xml:"id,attr"`
		Parts []struct {
			ID   string `xml:"id,attr"`
			Type string `xml:"type,attr"`
		} `xml:"part"`
	} `xml:"lib"`
}

// ─── loading ────────────────────────────────────────────────────────────────

// LoadPack reads the manifest and figure data for one era's asset root.
func LoadPack(root string) (*Pack, error) {
	pack := &Pack{
		Root:      root,
		Sprites:   map[string]SpriteEntry{},
		Palettes:  map[string]map[string]Colour{},
		SetTypes:  map[string]SetType{},
		libraries: map[string]string{},
	}

	manifestBytes, err := os.ReadFile(filepath.Join(root, "manifest.json"))
	if err != nil {
		return nil, fmt.Errorf("read manifest: %w", err)
	}
	var manifest manifestFile
	if err := json.Unmarshal(manifestBytes, &manifest); err != nil {
		return nil, fmt.Errorf("parse manifest: %w", err)
	}
	pack.Sprites = manifest.Sprites

	if err := pack.loadFigureData(filepath.Join(root, "figuredata.xml")); err != nil {
		return nil, err
	}
	if err := pack.loadFigureMap(filepath.Join(root, "figuremap.xml")); err != nil {
		return nil, err
	}
	return pack, nil
}

func (p *Pack) loadFigureData(path string) error {
	data, err := os.ReadFile(path)
	if err != nil {
		return fmt.Errorf("read figuredata: %w", err)
	}

	var parsed figureDataXML
	if err := xml.Unmarshal(data, &parsed); err != nil {
		return fmt.Errorf("parse figuredata: %w", err)
	}

	for _, palette := range parsed.Colors.Palettes {
		colours := map[string]Colour{}
		for _, c := range palette.Colors {
			colours[c.ID] = Colour{ID: c.ID, RGB: parseHexColour(c.Value)}
		}
		p.Palettes[palette.ID] = colours
	}

	for _, st := range parsed.Sets.SetTypes {
		sets := map[string]Set{}
		for _, s := range st.Sets {
			parts := make([]Part, 0, len(s.Parts))
			for _, part := range s.Parts {
				partType := part.Type
				if partType == "" {
					partType = st.Type
				}
				colourIndex, err := strconv.Atoi(part.ColourIndex)
				if err != nil || colourIndex < 1 {
					colourIndex = 1
				}
				parts = append(parts, Part{
					ID:          part.ID,
					Type:        partType,
					Index:       part.Index,
					Colourable:  part.Colorable == "1",
					ColourIndex: colourIndex,
				})
			}
			sets[s.ID] = Set{ID: s.ID, Parts: parts}
		}
		p.SetTypes[st.Type] = SetType{Type: st.Type, PaletteID: st.PaletteID, Sets: sets}
	}
	return nil
}

func (p *Pack) loadFigureMap(path string) error {
	data, err := os.ReadFile(path)
	if err != nil {
		return fmt.Errorf("read figuremap: %w", err)
	}

	var parsed figureMapXML
	if err := xml.Unmarshal(data, &parsed); err != nil {
		return fmt.Errorf("parse figuremap: %w", err)
	}

	for _, lib := range parsed.Libs {
		for _, part := range lib.Parts {
			p.libraries[part.Type+":"+part.ID] = lib.ID
		}
	}
	return nil
}

// ─── lookups ────────────────────────────────────────────────────────────────

// LibraryFor returns the sprite library holding a part, if the pack has it.
func (p *Pack) LibraryFor(partType, partID string) (string, bool) {
	lib, ok := p.libraries[partType+":"+partID]
	return lib, ok
}

// ColourFor resolves a colour id within the palette belonging to a set type.
// The same id means different things for hair and for a shirt, so the set type
// has to be part of the lookup.
func (p *Pack) ColourFor(setType, colourID string) (Colour, bool) {
	st, ok := p.SetTypes[setType]
	if !ok {
		return Colour{}, false
	}
	palette, ok := p.Palettes[st.PaletteID]
	if !ok {
		return Colour{}, false
	}
	colour, ok := palette[colourID]
	return colour, ok
}

// Resolve follows any alias chain to the sprite that actually holds an image,
// reporting the accumulated mirroring and the requested entry's own offsets.
func (p *Pack) Resolve(name string) (entry SpriteEntry, flip bool, ok bool) {
	first, ok := p.Sprites[name]
	if !ok {
		return SpriteEntry{}, false, false
	}

	current := first
	flip = current.FlipH

	// A pack that points a sprite back at itself must not hang the renderer.
	for depth := 0; current.File == "" && current.Source != ""; depth++ {
		if depth > maxAliasDepth {
			return SpriteEntry{}, false, false
		}
		next, exists := p.Sprites[current.Source]
		if !exists {
			return SpriteEntry{}, false, false
		}
		current = next
		if current.FlipH {
			flip = !flip
		}
	}

	if current.File == "" {
		return SpriteEntry{}, false, false
	}

	// Draw the resolved image, but positioned by the requested entry's offsets.
	current.OffsetX = first.OffsetX
	current.OffsetY = first.OffsetY
	return current, flip, true
}

const maxAliasDepth = 8

func parseHexColour(text string) uint32 {
	hex := strings.TrimPrefix(strings.TrimSpace(text), "#")
	value, err := strconv.ParseUint(hex, 16, 32)
	if err != nil {
		return 0xFFFFFF
	}
	return uint32(value)
}
