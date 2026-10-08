package furni

import (
	"strings"
	"testing"
)

const sample = `<?xml version="1.0"?><furnidata><roomitemtypes>
<furnitype id="13" classname="shelves_norja"><category>shelf</category><xdim>1</xdim><ydim>2</ydim>
<name>Habbo Bookcase</name><description>It's O'Brien's</description><offerid>5</offerid>
<canstandon>0</canstandon><cansiton>0</cansiton><canlayon>0</canlayon><height>1.0E-4</height>
<furniline>habboween_2018</furniline><rare>0</rare><tradeable>1</tradeable></furnitype>
<furnitype id="14" classname="bed_x"><category>other</category><canlayon>1</canlayon><offerid>-1</offerid></furnitype>
</roomitemtypes><wallitemtypes>
<furnitype id="4001" classname="poster"><category>wall_decoration</category><name>Poster</name><offerid>9</offerid></furnitype>
</wallitemtypes></furnidata>`

func TestParseReadsRoomAndWallItems(t *testing.T) {
	items, err := Parse(strings.NewReader(sample))
	if err != nil {
		t.Fatal(err)
	}
	if len(items) != 3 {
		t.Fatalf("got %d items", len(items))
	}
	s := items[0]
	if s.Name != "Habnut Bookcase" || s.YDim != 2 || s.Height != 0.0001 || !s.OnSale || s.Wall {
		t.Errorf("bookcase read wrongly: %+v", s)
	}
	if !items[2].Wall || items[1].OnSale {
		t.Errorf("wall item or sale flag wrong: %+v %+v", items[2], items[1])
	}
}

func TestPlayersNeverReadTheOriginalName(t *testing.T) {
	for in, want := range map[string]string{
		"Habbo Club Sofa": "Habnut Club Sofa", "HABBO": "HABNUT", "habboween": "habnutween", "Habbóbora": "Habnutbora",
	} {
		if got := Rebrand(in); got != want {
			t.Errorf("Rebrand(%q) = %q, want %q", in, got, want)
		}
	}
	if got := LineCaption("habboween_2018"); got != "Habnutween 2018" {
		t.Errorf("caption %q", got)
	}
}

func TestSQLQuotesAndKeepsHandTunedValues(t *testing.T) {
	items, _ := Parse(strings.NewReader(sample))
	sql := SQL(items, Options{})
	for _, want := range []string{
		`'It\'s O\'Brien\'s'`,
		"'bed_x'", "'bed',1",
		"WHERE b.sprite_id IN ('shelves_norja')",
		"'Habnutween 2018'",
		// What an offer contains: the catalogue's items_json has no default.
		`CONCAT('[{"baseId":', b.id, ',"count":1}]')`,
	} {
		if !strings.Contains(sql, want) {
			t.Errorf("SQL lacks %s", want)
		}
	}
	update := sql[strings.Index(sql, "ON DUPLICATE KEY UPDATE"):]
	update = update[:strings.Index(update, ";")]
	if strings.Contains(update, "interaction_type") || strings.Contains(update, "interaction_modes") {
		t.Error("a re-import would overwrite interactions staff set by hand")
	}
	if strings.Contains(sql, "'bed_x', 3") || strings.Count(sql, "INSERT INTO habnut_catalogue_offers") != 2 {
		t.Error("an item not on sale was put in the catalogue, or a sale item was missed")
	}
}
