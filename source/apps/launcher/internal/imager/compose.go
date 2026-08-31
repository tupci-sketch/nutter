package imager

import "strconv"

// Composition decides which sprites make up an avatar, in what order, tinted
// which colour, and which are mirrored. It holds no image data, so the rules
// that are easy to get subtly wrong stay testable on their own.

// Size selects the sprite scale: "h" is the room-sized avatar, "sh" the small
// head used in lists and profile thumbnails.
type Size string

const (
	SizeBody Size = "h"
	SizeHead Size = "sh"
)

// Actions in the short form the sprite names use.
const (
	ActionStand   = "std"
	ActionWalk    = "wlk"
	ActionSit     = "sit"
	ActionLay     = "lay"
	ActionWave    = "wav"
	ActionRespect = "respect"
	ActionBlow    = "blow"
)

// drawOrder lists part types back to front.
//
// figuredata carries a per-part index, but it only orders parts within one set,
// so this table decides which slot sits in front of which. It is the difference
// between a face drawn over hair and hair drawn over a face.
var drawOrder = []string{
	"li",  // left-hand item, behind everything
	"lh",  // left hand
	"ls",  // left sleeve
	"bd",  // body
	"sh",  // shoes
	"lg",  // legs
	"ch",  // chest / shirt
	"wa",  // waist
	"ca",  // chest accessory
	"cc",  // coat
	"hd",  // head
	"fc",  // face
	"ey",  // eyes
	"hr",  // hair
	"hrb", // hair below a hat
	"ha",  // hat
	"he",  // head accessory
	"ea",  // glasses
	"fa",  // face accessory
	"rh",  // right hand
	"rs",  // right sleeve
	"ri",  // right-hand item, in front
}

var drawRank = func() map[string]int {
	ranks := make(map[string]int, len(drawOrder))
	for i, t := range drawOrder {
		ranks[t] = i
	}
	return ranks
}()

// DrawOrderOf ranks a part type. Unknown types sort to the front so a part the
// table has never heard of cannot hide the face.
func DrawOrderOf(partType string) int {
	if rank, ok := drawRank[partType]; ok {
		return rank
	}
	return len(drawOrder)
}

// headParts follow the head's direction rather than the body's.
var headParts = map[string]bool{
	"hd": true, "fc": true, "ey": true, "hr": true, "hrb": true,
	"ha": true, "he": true, "ea": true, "fa": true,
}

// bodyOnlyActions change the body alone. A waving avatar still has an ordinary
// head, and packs ship no head sprite for those actions, so head parts fall
// back to standing rather than vanishing.
var bodyOnlyActions = map[string]bool{
	ActionWave: true, ActionRespect: true, ActionBlow: true,
}

// Selection is one segment of a parsed figure string.
type Selection struct {
	Type    string
	SetID   string
	Colours []string
}

// Layer is one sprite to draw.
type Layer struct {
	// Sprite is the name to look up in the pack manifest.
	Sprite string
	// Z is the draw order, back to front.
	Z int
	// Tint is 0xRRGGBB, applied only when HasTint is set.
	Tint    uint32
	HasTint bool
	// Flip marks a direction the pack stores only as its mirror.
	Flip bool
	// Type is the part type, kept for diagnostics and for hiding slots.
	Type string
}

// Request describes one avatar to compose.
type Request struct {
	Figure string
	// Direction the body faces, 0-7.
	Direction int
	// HeadDirection may differ when a user looks around; defaults to Direction.
	HeadDirection *int
	Action        string
	Frame         int
	Size          Size
	// Hide leaves slots out, for example hair under a hat.
	Hide []string
}

// MirrorDirection maps a compass direction onto the sprites a pack actually
// ships.
//
// Directions 0-3 and 7 are stored as drawn. Directions 4, 5 and 6 are not in
// the pack at all: they are 2, 1 and 0 mirrored. Composing them any other way
// leaves a quarter of the compass with no avatar.
func MirrorDirection(direction int) (int, bool) {
	d := ((direction % 8) + 8) % 8
	if d > 3 && d < 7 {
		return 6 - d, true
	}
	return d, false
}

// ParseFigureString reads a figure such as "hd-180-1.ch-255-66.lg-280-110".
//
// Malformed segments are dropped rather than failing the whole call: a figure
// string arrives from the database and from other players, and one bad segment
// should cost a hat, not the entire avatar.
func ParseFigureString(figure string) []Selection {
	if figure == "" {
		return nil
	}

	seen := map[string]bool{}
	var out []Selection

	for _, segment := range splitNonEmpty(figure, '.') {
		bits := splitNonEmpty(segment, '-')
		if len(bits) < 2 {
			continue
		}
		partType, setID := bits[0], bits[1]
		// A repeated slot would draw twice; the first wins.
		if seen[partType] {
			continue
		}
		seen[partType] = true
		out = append(out, Selection{Type: partType, SetID: setID, Colours: bits[2:]})
	}
	return out
}

// BuildFigureString renders selections back to a figure string.
func BuildFigureString(parts []Selection) string {
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		segment := p.Type + "-" + p.SetID
		for _, colour := range p.Colours {
			segment += "-" + colour
		}
		out = append(out, segment)
	}
	return joinWith(out, ".")
}

// Compose builds the ordered layer list for a figure.
//
// Parts whose library the pack does not know are skipped: a pack missing one
// hairstyle should cost that hairstyle, not the whole avatar.
func (p *Pack) Compose(req Request) []Layer {
	action := req.Action
	if action == "" {
		action = ActionStand
	}
	size := req.Size
	if size == "" {
		size = SizeBody
	}
	headDirection := req.Direction
	if req.HeadDirection != nil {
		headDirection = *req.HeadDirection
	}

	hidden := map[string]bool{}
	for _, h := range req.Hide {
		hidden[h] = true
	}

	var layers []Layer
	for _, selection := range ParseFigureString(req.Figure) {
		if hidden[selection.Type] {
			continue
		}

		setType, ok := p.SetTypes[selection.Type]
		if !ok {
			continue
		}
		set, ok := setType.Sets[selection.SetID]
		if !ok {
			continue
		}

		for _, part := range set.Parts {
			if hidden[part.Type] {
				continue
			}
			library, ok := p.LibraryFor(part.Type, part.ID)
			if !ok {
				continue
			}

			isHead := headParts[part.Type]
			partAction := action
			if isHead && bodyOnlyActions[action] {
				partAction = ActionStand
			}
			facing := req.Direction
			if isHead {
				facing = headDirection
			}
			drawn, flip := MirrorDirection(facing)

			tint, hasTint := p.tintFor(selection, part)
			layers = append(layers, Layer{
				Sprite: SpriteName(SpriteNameParts{
					Library:   library,
					Size:      size,
					Action:    partAction,
					Type:      part.Type,
					PartID:    part.ID,
					Direction: drawn,
					Frame:     req.Frame,
				}),
				Z:       DrawOrderOf(part.Type)*10 + part.Index,
				Tint:    tint,
				HasTint: hasTint,
				Flip:    flip,
				Type:    part.Type,
			})
		}
	}

	sortLayers(layers)
	return layers
}

// tintFor picks the tint for one part.
//
// Colourable parts ship as greyscale masks and take their colour from the
// figure string; the part's colour index chooses which of the figure's colour
// slots applies. A part that is not colourable keeps the artwork's own colours
// and must never be tinted.
func (p *Pack) tintFor(selection Selection, part Part) (uint32, bool) {
	if !part.Colourable {
		return 0, false
	}
	index := part.ColourIndex - 1
	if index < 0 || index >= len(selection.Colours) {
		return 0, false
	}
	colour, ok := p.ColourFor(selection.Type, selection.Colours[index])
	if !ok {
		return 0, false
	}
	return colour.RGB, true
}

// SpriteNameParts are the fields of a sprite name.
type SpriteNameParts struct {
	Library   string
	Size      Size
	Action    string
	Type      string
	PartID    string
	Direction int
	Frame     int
}

// SpriteName builds a name in the form the pack uses:
// {library}_{size}_{action}_{type}_{partId}_{direction}_{frame},
// for example hh_human_hair_h_std_hr_828_2_0.
func SpriteName(parts SpriteNameParts) string {
	return joinWith([]string{
		parts.Library,
		string(parts.Size),
		parts.Action,
		parts.Type,
		parts.PartID,
		strconv.Itoa(parts.Direction),
		strconv.Itoa(parts.Frame),
	}, "_")
}

// ─── small helpers ──────────────────────────────────────────────────────────

// sortLayers orders back to front, keeping equal ranks in the order figuredata
// listed them so a set's own part order still decides ties.
func sortLayers(layers []Layer) {
	for i := 1; i < len(layers); i++ {
		for j := i; j > 0 && layers[j].Z < layers[j-1].Z; j-- {
			layers[j], layers[j-1] = layers[j-1], layers[j]
		}
	}
}

func splitNonEmpty(text string, sep byte) []string {
	var out []string
	start := 0
	for i := 0; i <= len(text); i++ {
		if i == len(text) || text[i] == sep {
			if i > start {
				out = append(out, text[start:i])
			}
			start = i + 1
		}
	}
	return out
}

func joinWith(parts []string, sep string) string {
	out := ""
	for i, part := range parts {
		if i > 0 {
			out += sep
		}
		out += part
	}
	return out
}
