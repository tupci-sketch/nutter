// Package furni turns a client build's furniture data into the hotel's own
// furniture and catalogue rows.
//
// The client draws a piece of furniture from the build's artwork, found by its
// classname; the hotel knows it as a row in habnut_items_base whose sprite_id
// must be that classname, or it is a piece the hotel can place and the client
// can never draw. This writes those rows from the same furnidata the client
// reads, so the two cannot disagree, and puts what the live game sells into
// the catalogue, one page per furniture line as the original catalogue is laid
// out.
//
// Running it again after a newer build is installed adds what is new and
// refreshes what changed. It never overwrites what staff have tuned by hand —
// an item's interaction, an offer's price — and never removes anything: rooms
// and inventories may hold items a later build no longer lists.
package furni

import (
	"encoding/xml"
	"fmt"
	"io"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

// Item is one furnitype from a furnidata file.
type Item struct {
	Classname   string
	Revision    int
	Wall        bool
	Name        string
	Description string
	Category    string
	Line        string
	Environment string
	XDim, YDim  int
	Height      float64
	CanSit      bool
	CanStand    bool
	CanLay      bool
	CanPutOn    bool
	Tradeable   bool
	Recyclable  bool
	OnSale      bool
	Rare        bool
}

type xmlType struct {
	Classname   string `xml:"classname,attr"`
	Revision    string `xml:"revision"`
	Name        string `xml:"name"`
	Description string `xml:"description"`
	Category    string `xml:"category"`
	Line        string `xml:"furniline"`
	Environment string `xml:"environment"`
	XDim        string `xml:"xdim"`
	YDim        string `xml:"ydim"`
	Height      string `xml:"height"`
	CanSit      string `xml:"cansiton"`
	CanStand    string `xml:"canstandon"`
	CanLay      string `xml:"canlayon"`
	CanPutOn    string `xml:"canputstuffon"`
	Tradeable   string `xml:"tradeable"`
	Recyclable  string `xml:"recyclable"`
	OfferID     string `xml:"offerid"`
	Rare        string `xml:"rare"`
}

type xmlData struct {
	Room []xmlType `xml:"roomitemtypes>furnitype"`
	Wall []xmlType `xml:"wallitemtypes>furnitype"`
}

// Parse reads a furnidata XML file.
func Parse(r io.Reader) ([]Item, error) {
	var d xmlData
	if err := xml.NewDecoder(r).Decode(&d); err != nil {
		return nil, fmt.Errorf("not a furnidata file: %w", err)
	}
	var items []Item
	add := func(list []xmlType, wall bool) {
		for _, t := range list {
			name := strings.TrimSpace(t.Classname)
			if name == "" || len(name) > 128 {
				continue
			}
			items = append(items, Item{
				Classname:   name,
				Revision:    bounded(t.Revision, 0, 0, 1<<30),
				Wall:        wall,
				Name:        clip(Rebrand(strings.TrimSpace(t.Name)), 128),
				Description: clip(Rebrand(strings.TrimSpace(t.Description)), 512),
				Category:    strings.TrimSpace(t.Category),
				Line:        clip(strings.TrimSpace(t.Line), 64),
				Environment: clip(strings.TrimSpace(t.Environment), 64),
				XDim:        bounded(t.XDim, 1, 1, 64),
				YDim:        bounded(t.YDim, 1, 1, 64),
				Height:      height(t.Height),
				CanSit:      t.CanSit == "1",
				CanStand:    t.CanStand == "1",
				CanLay:      t.CanLay == "1",
				CanPutOn:    t.CanPutOn == "1",
				Tradeable:   t.Tradeable != "0",
				Recyclable:  t.Recyclable == "1",
				OnSale:      bounded(t.OfferID, -1, -1, 1<<30) > 0,
				Rare:        t.Rare == "1",
			})
		}
	}
	add(d.Room, false)
	add(d.Wall, true)
	if len(items) == 0 {
		return nil, fmt.Errorf("the furnidata lists no furniture")
	}
	return items, nil
}

var habbo = regexp.MustCompile(`(?i)habbo`)

// Rebrand puts the hotel's own name where the original's appears. Item names
// and descriptions are read by players, and this is Habnut.
func Rebrand(s string) string {
	return habbo.ReplaceAllStringFunc(s, func(m string) string {
		switch {
		case m == strings.ToUpper(m):
			return "HABNUT"
		case m[0] >= 'A' && m[0] <= 'Z':
			return "Habnut"
		default:
			return "habnut"
		}
	})
}

// LineCaption is how a furniture line is titled in the catalogue.
func LineCaption(line string) string {
	if line == "" {
		return "Assorted"
	}
	words := strings.FieldsFunc(Rebrand(line), func(r rune) bool { return r == '_' || r == '-' || r == ' ' })
	for i, w := range words {
		words[i] = strings.ToUpper(w[:1]) + w[1:]
	}
	return clip(strings.Join(words, " "), 64)
}

// interactionModes is how many states a new item steps through when used.
// The build does not say; lights and gates are the common two-state kinds.
func interactionModes(it Item) int {
	switch it.Category {
	case "lighting", "gate", "dimmer":
		return 2
	}
	return 1
}

func interactionType(it Item) string {
	switch {
	case it.CanLay:
		return "bed"
	case it.Category == "gate":
		return "gate"
	}
	return "default"
}

// Artwork is the file a piece of furniture is drawn from: colour variants
// ("chair_norja*2") share their base item's file.
func (it Item) Artwork() string {
	if i := strings.IndexByte(it.Classname, '*'); i > 0 {
		return it.Classname[:i]
	}
	return it.Classname
}

// Options shape the catalogue part.
type Options struct {
	Price      int // credits for an imported offer
	ParentPage string
}

// SQL writes the statements that bring the hotel in line with items.
func SQL(items []Item, opt Options) string {
	if opt.Price <= 0 {
		opt.Price = 3
	}
	if opt.ParentPage == "" {
		opt.ParentPage = "Furni Lines"
	}
	var b strings.Builder
	b.WriteString("-- Habnut furniture import: generated, do not edit by hand.\nSET NAMES utf8mb4;\nSTART TRANSACTION;\n")

	for start := 0; start < len(items); start += 400 {
		end := min(start+400, len(items))
		b.WriteString("INSERT INTO habnut_items_base (sprite_id, name, description, type, width, length, " +
			"stack_height, can_stack, can_sit, is_walkable, is_tradeable, is_recyclable, " +
			"interaction_type, interaction_modes, furni_line_id, environment) VALUES\n")
		for i, it := range items[start:end] {
			kind := "floor"
			if it.Wall {
				kind = "wall"
			}
			if i > 0 {
				b.WriteString(",\n")
			}
			fmt.Fprintf(&b, "(%s,%s,%s,'%s',%d,%d,%.3f,%d,%d,%d,%d,%d,%s,%d,%s,%s)",
				q(it.Classname), q(it.Name), q(it.Description), kind, it.XDim, it.YDim, it.Height,
				b2i(it.CanPutOn), b2i(it.CanSit), b2i(it.CanStand), b2i(it.Tradeable), b2i(it.Recyclable),
				q(interactionType(it)), interactionModes(it), q(it.Line), q(it.Environment))
		}
		// interaction_type and interaction_modes are left as they are on
		// rows that exist: staff may have set them by hand.
		b.WriteString("\nON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), " +
			"type=VALUES(type), width=VALUES(width), length=VALUES(length), stack_height=VALUES(stack_height), " +
			"can_stack=VALUES(can_stack), can_sit=VALUES(can_sit), is_walkable=VALUES(is_walkable), " +
			"is_tradeable=VALUES(is_tradeable), is_recyclable=VALUES(is_recyclable), " +
			"furni_line_id=VALUES(furni_line_id), environment=VALUES(environment);\n")
	}

	// The catalogue: what the live game sells, a page per furniture line,
	// under one parent page. Pages are found again by name on later runs.
	byLine := map[string][]Item{}
	for _, it := range items {
		if it.OnSale && !it.Rare {
			byLine[it.Line] = append(byLine[it.Line], it)
		}
	}
	lines := make([]string, 0, len(byLine))
	for l := range byLine {
		lines = append(lines, l)
	}
	sort.Slice(lines, func(i, j int) bool { return LineCaption(lines[i]) < LineCaption(lines[j]) })

	parent := q(opt.ParentPage)
	fmt.Fprintf(&b, "INSERT INTO habnut_catalogue_pages (parent_id, name, caption, visible, world_id, layout, order_index, min_rank, enabled) "+
		"SELECT NULL, %s, %s, 1, 'both', 'default_3x3', 50, 0, 1 FROM DUAL "+
		"WHERE NOT EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE name = %s AND parent_id IS NULL);\n", parent, parent, parent)
	fmt.Fprintf(&b, "SET @furni_parent = (SELECT id FROM habnut_catalogue_pages WHERE name = %s AND parent_id IS NULL ORDER BY id LIMIT 1);\n", parent)

	for i, line := range lines {
		caption := q(LineCaption(line))
		fmt.Fprintf(&b, "INSERT INTO habnut_catalogue_pages (parent_id, name, caption, visible, world_id, layout, order_index, min_rank, enabled) "+
			"SELECT @furni_parent, %s, %s, 1, 'both', 'default_3x3', %d, 0, 1 FROM DUAL "+
			"WHERE NOT EXISTS (SELECT 1 FROM habnut_catalogue_pages WHERE parent_id = @furni_parent AND name = %s);\n",
			caption, caption, i, caption)
		fmt.Fprintf(&b, "SET @page = (SELECT id FROM habnut_catalogue_pages WHERE parent_id = @furni_parent AND name = %s ORDER BY id LIMIT 1);\n", caption)

		group := byLine[line]
		sort.Slice(group, func(i, j int) bool { return group[i].Classname < group[j].Classname })
		for start := 0; start < len(group); start += 200 {
			end := min(start+200, len(group))
			b.WriteString("INSERT INTO habnut_catalogue_offers (page_id, base_id, name, description, credits_price, order_index, is_visible) " +
				"SELECT @page, b.id, b.name, b.description, " + strconv.Itoa(opt.Price) + ", 0, 1 " +
				"FROM habnut_items_base b WHERE b.sprite_id IN (")
			for k, it := range group[start:end] {
				if k > 0 {
					b.WriteString(",")
				}
				b.WriteString(q(it.Classname))
			}
			// An item already on sale anywhere is left where it is, at the
			// price it has.
			b.WriteString(") AND NOT EXISTS (SELECT 1 FROM habnut_catalogue_offers o WHERE o.base_id = b.id);\n")
		}
	}
	b.WriteString("COMMIT;\n")
	return b.String()
}

func q(s string) string {
	r := strings.NewReplacer(`\`, `\\`, `'`, `\'`, "\x00", "", "\n", `\n`, "\r", `\r`, "\x1a", `\Z`)
	return "'" + r.Replace(s) + "'"
}

func b2i(v bool) int {
	if v {
		return 1
	}
	return 0
}

func bounded(s string, def, lo, hi int) int {
	n, err := strconv.Atoi(strings.TrimSpace(s))
	if err != nil {
		return def
	}
	return max(lo, min(hi, n))
}

func height(s string) float64 {
	f, err := strconv.ParseFloat(strings.TrimSpace(s), 64)
	if err != nil || f < 0 {
		return 1
	}
	return min(f, 999)
}

func clip(s string, n int) string {
	r := []rune(s)
	if len(r) > n {
		return string(r[:n])
	}
	return s
}
