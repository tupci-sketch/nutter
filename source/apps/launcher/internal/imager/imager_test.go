package imager

import (
	"bytes"
	"encoding/json"
	"fmt"
	"image"
	"image/color"
	"image/png"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

// ─── fixtures ───────────────────────────────────────────────────────────────

// solidPNG writes a rectangle of one colour, used as stand-in artwork.
func solidPNG(t *testing.T, path string, w, h int, c color.NRGBA) {
	t.Helper()
	img := image.NewNRGBA(image.Rect(0, 0, w, h))
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			img.SetNRGBA(x, y, c)
		}
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
		t.Fatal(err)
	}
	f, err := os.Create(path)
	if err != nil {
		t.Fatal(err)
	}
	defer f.Close()
	if err := png.Encode(f, img); err != nil {
		t.Fatal(err)
	}
}

const testFigureData = `<?xml version="1.0"?>
<figuredata>
  <colors>
    <palette id="1">
      <color id="1" index="1" club="0" selectable="1">FFCB98</color>
      <color id="2" index="2" club="0" selectable="1">543D35</color>
    </palette>
    <palette id="3">
      <color id="61" index="1" club="0" selectable="1">C99263</color>
      <color id="92" index="2" club="0" selectable="1">FF0000</color>
    </palette>
  </colors>
  <sets>
    <settype type="hd" paletteid="1">
      <set id="180" gender="M" club="0" colorable="1" selectable="1">
        <part id="180" type="hd" colorable="1" index="0" colorindex="1"/>
      </set>
    </settype>
    <settype type="hr" paletteid="3">
      <set id="828" gender="M" club="0" colorable="1" selectable="1">
        <part id="828" type="hr" colorable="1" index="0" colorindex="1"/>
        <part id="828" type="hrb" colorable="1" index="1" colorindex="1"/>
      </set>
    </settype>
    <settype type="ch" paletteid="3">
      <set id="210" gender="M" club="0" colorable="0" selectable="1">
        <part id="110" type="ch" colorable="0" index="0" colorindex="1"/>
      </set>
    </settype>
  </sets>
</figuredata>`

const testFigureMap = `<?xml version="1.0"?>
<map>
  <lib id="hh_human_body" revision="1">
    <part id="180" type="hd"/>
  </lib>
  <lib id="hh_human_hair" revision="1">
    <part id="828" type="hr"/>
    <part id="828" type="hrb"/>
  </lib>
  <lib id="hh_human_shirt" revision="1">
    <part id="110" type="ch"/>
  </lib>
</map>`

// writePack lays out a minimal but complete asset pack on disk.
func writePack(t *testing.T, sprites map[string]SpriteEntry) string {
	t.Helper()
	root := t.TempDir()

	manifest, err := json.Marshal(manifestFile{Sprites: sprites})
	if err != nil {
		t.Fatal(err)
	}
	write := func(name, content string) {
		if err := os.WriteFile(filepath.Join(root, name), []byte(content), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	write("manifest.json", string(manifest))
	write("figuredata.xml", testFigureData)
	write("figuremap.xml", testFigureMap)
	return root
}

// standardPack is a pack with artwork for every part the test figure uses.
func standardPack(t *testing.T) string {
	t.Helper()
	sprites := map[string]SpriteEntry{
		"hh_human_body_h_std_hd_180_2_0":  {File: "figure/head.png", Width: 8, Height: 8, OffsetX: 4, OffsetY: 40},
		"hh_human_hair_h_std_hr_828_2_0":  {File: "figure/hair.png", Width: 8, Height: 4, OffsetX: 4, OffsetY: 44},
		"hh_human_hair_h_std_hrb_828_2_0": {File: "figure/hairb.png", Width: 8, Height: 2, OffsetX: 4, OffsetY: 42},
		"hh_human_shirt_h_std_ch_110_2_0": {File: "figure/shirt.png", Width: 10, Height: 12, OffsetX: 5, OffsetY: 32},
	}
	root := writePack(t, sprites)
	solidPNG(t, filepath.Join(root, "figure/head.png"), 8, 8, color.NRGBA{255, 255, 255, 255})
	solidPNG(t, filepath.Join(root, "figure/hair.png"), 8, 4, color.NRGBA{128, 128, 128, 255})
	solidPNG(t, filepath.Join(root, "figure/hairb.png"), 8, 2, color.NRGBA{128, 128, 128, 255})
	solidPNG(t, filepath.Join(root, "figure/shirt.png"), 10, 12, color.NRGBA{0, 0, 255, 255})
	return root
}

const testFigure = "hd-180-1.hr-828-92.ch-210"

// ─── pack loading ───────────────────────────────────────────────────────────

func TestLoadPackReadsPalettesSetsAndLibraries(t *testing.T) {
	pack, err := LoadPack(standardPack(t))
	if err != nil {
		t.Fatalf("LoadPack: %v", err)
	}

	if got := len(pack.SetTypes); got != 3 {
		t.Errorf("set types = %d, want 3", got)
	}
	if lib, ok := pack.LibraryFor("hr", "828"); !ok || lib != "hh_human_hair" {
		t.Errorf("library for hr:828 = %q, %v", lib, ok)
	}
	colour, ok := pack.ColourFor("hr", "92")
	if !ok || colour.RGB != 0xFF0000 {
		t.Errorf("colour hr:92 = %06X, %v; want FF0000", colour.RGB, ok)
	}
}

func TestColoursAreScopedToTheirSetType(t *testing.T) {
	pack, err := LoadPack(standardPack(t))
	if err != nil {
		t.Fatal(err)
	}

	// Colour 1 exists in the skin palette but not the hair one, so the same id
	// must not resolve for hair.
	if _, ok := pack.ColourFor("hd", "1"); !ok {
		t.Error("colour 1 should resolve for hd")
	}
	if _, ok := pack.ColourFor("hr", "1"); ok {
		t.Error("colour 1 must not resolve for hr: it belongs to another palette")
	}
}

func TestLoadPackReportsMissingFiles(t *testing.T) {
	if _, err := LoadPack(t.TempDir()); err == nil {
		t.Fatal("expected an error for a directory with no manifest")
	}
}

// ─── alias resolution ───────────────────────────────────────────────────────

func TestResolveFollowsAliasAndMirrors(t *testing.T) {
	root := writePack(t, map[string]SpriteEntry{
		"real":  {File: "figure/head.png", Width: 8, Height: 8, OffsetX: 4, OffsetY: 40},
		"alias": {Source: "real", FlipH: true, OffsetX: 9, OffsetY: 40},
	})
	solidPNG(t, filepath.Join(root, "figure/head.png"), 8, 8, color.NRGBA{255, 255, 255, 255})

	pack, err := LoadPack(root)
	if err != nil {
		t.Fatal(err)
	}

	entry, flip, ok := pack.Resolve("alias")
	if !ok {
		t.Fatal("alias did not resolve")
	}
	if entry.File != "figure/head.png" {
		t.Errorf("file = %q, want the aliased file", entry.File)
	}
	if !flip {
		t.Error("alias marked flipH should resolve mirrored")
	}
	// The alias carries its own placement; only the pixels come from the target.
	if entry.OffsetX != 9 {
		t.Errorf("offsetX = %d, want the alias's own 9", entry.OffsetX)
	}
}

func TestResolveRefusesAliasLoops(t *testing.T) {
	root := writePack(t, map[string]SpriteEntry{
		"a": {Source: "b"},
		"b": {Source: "a"},
	})
	pack, err := LoadPack(root)
	if err != nil {
		t.Fatal(err)
	}
	if _, _, ok := pack.Resolve("a"); ok {
		t.Error("a loop must not resolve")
	}
}

func TestResolveReportsUnknownSprite(t *testing.T) {
	pack, err := LoadPack(standardPack(t))
	if err != nil {
		t.Fatal(err)
	}
	if _, _, ok := pack.Resolve("no_such_sprite"); ok {
		t.Error("an unknown sprite must not resolve")
	}
}

// ─── figure strings ─────────────────────────────────────────────────────────

func TestParseFigureString(t *testing.T) {
	parts := ParseFigureString("hd-180-1.hr-828-61-92.ch-210")
	if len(parts) != 3 {
		t.Fatalf("parts = %d, want 3", len(parts))
	}
	if parts[1].Type != "hr" || parts[1].SetID != "828" {
		t.Errorf("second part = %+v", parts[1])
	}
	if len(parts[1].Colours) != 2 || parts[1].Colours[1] != "92" {
		t.Errorf("colours = %v, want two ending in 92", parts[1].Colours)
	}
}

func TestParseFigureStringDropsBadSegments(t *testing.T) {
	// A segment with no set id names no artwork, so it is dropped rather than
	// costing the whole figure.
	parts := ParseFigureString("hd-180-1..hr.ch-210-1")
	if len(parts) != 2 {
		t.Fatalf("parts = %d, want 2: %+v", len(parts), parts)
	}
	if parts[1].Type != "ch" {
		t.Errorf("second part = %q, want ch", parts[1].Type)
	}
}

func TestParseFigureStringKeepsFirstOfARepeatedSlot(t *testing.T) {
	parts := ParseFigureString("hr-828-92.hr-100-1")
	if len(parts) != 1 || parts[0].SetID != "828" {
		t.Errorf("parts = %+v, want only the first hair", parts)
	}
}

func TestBuildFigureStringRoundTrips(t *testing.T) {
	const figure = "hd-180-1.hr-828-61-92.ch-210"
	if got := BuildFigureString(ParseFigureString(figure)); got != figure {
		t.Errorf("round trip = %q, want %q", got, figure)
	}
}

// ─── direction mirroring ────────────────────────────────────────────────────

func TestMirrorDirection(t *testing.T) {
	cases := []struct {
		in   int
		want int
		flip bool
	}{
		{0, 0, false}, {1, 1, false}, {2, 2, false}, {3, 3, false},
		{4, 2, true}, {5, 1, true}, {6, 0, true}, {7, 7, false},
		// Directions wrap, including from below zero.
		{8, 0, false}, {12, 2, true}, {-1, 7, false}, {-4, 2, true},
	}
	for _, c := range cases {
		got, flip := MirrorDirection(c.in)
		if got != c.want || flip != c.flip {
			t.Errorf("MirrorDirection(%d) = %d, %v; want %d, %v", c.in, got, flip, c.want, c.flip)
		}
	}
}

// ─── composition ────────────────────────────────────────────────────────────

func loadStandardPack(t *testing.T) *Pack {
	t.Helper()
	pack, err := LoadPack(standardPack(t))
	if err != nil {
		t.Fatal(err)
	}
	return pack
}

func TestComposeOrdersPartsBackToFront(t *testing.T) {
	layers := loadStandardPack(t).Compose(Request{Figure: testFigure, Direction: 2})

	var order []string
	for _, layer := range layers {
		order = append(order, layer.Type)
	}
	want := []string{"ch", "hd", "hr", "hrb"}
	if fmt.Sprint(order) != fmt.Sprint(want) {
		t.Errorf("draw order = %v, want %v", order, want)
	}
}

func TestComposeTintsOnlyColourableParts(t *testing.T) {
	layers := loadStandardPack(t).Compose(Request{Figure: testFigure, Direction: 2})

	for _, layer := range layers {
		switch layer.Type {
		case "hr", "hrb":
			if !layer.HasTint || layer.Tint != 0xFF0000 {
				t.Errorf("%s tint = %06X, %v; want FF0000", layer.Type, layer.Tint, layer.HasTint)
			}
		case "ch":
			// The shirt is not colourable, so its own artwork must survive.
			if layer.HasTint {
				t.Errorf("ch was tinted %06X but is not colourable", layer.Tint)
			}
		}
	}
}

func TestComposeLeavesOutPartsWithNoColourInTheFigure(t *testing.T) {
	// "hr-828" names a hairstyle without saying what colour, so the hair keeps
	// the artwork's own shade rather than being tinted an arbitrary one.
	layers := loadStandardPack(t).Compose(Request{Figure: "hr-828", Direction: 2})
	if len(layers) == 0 {
		t.Fatal("expected hair layers")
	}
	for _, layer := range layers {
		if layer.HasTint {
			t.Errorf("%s was tinted with no colour in the figure string", layer.Type)
		}
	}
}

func TestComposeMirrorsDirectionsThePackDoesNotShip(t *testing.T) {
	layers := loadStandardPack(t).Compose(Request{Figure: testFigure, Direction: 4})
	if len(layers) == 0 {
		t.Fatal("expected layers")
	}
	for _, layer := range layers {
		if !layer.Flip {
			t.Errorf("%s should be mirrored for direction 4", layer.Type)
		}
		// Direction 4 is drawn from the sprites for direction 2.
		if got := layer.Sprite[len(layer.Sprite)-3:]; got != "2_0" {
			t.Errorf("sprite %q should end in direction 2", layer.Sprite)
		}
	}
}

func TestComposeLetsTheHeadFaceElsewhere(t *testing.T) {
	head := 4
	layers := loadStandardPack(t).Compose(Request{
		Figure: testFigure, Direction: 2, HeadDirection: &head,
	})

	for _, layer := range layers {
		isHead := headParts[layer.Type]
		if isHead != layer.Flip {
			t.Errorf("%s flip = %v; only head parts follow the head direction",
				layer.Type, layer.Flip)
		}
	}
}

func TestComposeKeepsTheHeadStandingDuringBodyOnlyActions(t *testing.T) {
	layers := loadStandardPack(t).Compose(Request{
		Figure: testFigure, Direction: 2, Action: ActionWave,
	})

	for _, layer := range layers {
		if headParts[layer.Type] {
			if !contains(layer.Sprite, "_std_") {
				t.Errorf("head part %q should keep the standing action while waving", layer.Sprite)
			}
			continue
		}
		if !contains(layer.Sprite, "_wav_") {
			t.Errorf("body part %q should carry the wave action", layer.Sprite)
		}
	}
}

func TestComposeHidesRequestedSlots(t *testing.T) {
	layers := loadStandardPack(t).Compose(Request{
		Figure: testFigure, Direction: 2, Hide: []string{"hr", "hrb"},
	})
	for _, layer := range layers {
		if layer.Type == "hr" || layer.Type == "hrb" {
			t.Errorf("%s was hidden but still composed", layer.Type)
		}
	}
	if len(layers) != 2 {
		t.Errorf("layers = %d, want the head and shirt", len(layers))
	}
}

func TestComposeSkipsPartsThePackHasNoLibraryFor(t *testing.T) {
	// A figure naming a set the pack has never heard of should cost that slot
	// only.
	layers := loadStandardPack(t).Compose(Request{Figure: "hd-180-1.zz-999-1", Direction: 2})
	if len(layers) != 1 || layers[0].Type != "hd" {
		t.Errorf("layers = %+v, want just the head", layers)
	}
}

func TestSpriteNameFollowsThePackConvention(t *testing.T) {
	got := SpriteName(SpriteNameParts{
		Library: "hh_human_hair", Size: SizeBody, Action: ActionStand,
		Type: "hr", PartID: "828", Direction: 2, Frame: 0,
	})
	if want := "hh_human_hair_h_std_hr_828_2_0"; got != want {
		t.Errorf("SpriteName = %q, want %q", got, want)
	}
}

func TestDrawOrderPutsUnknownTypesInFront(t *testing.T) {
	if DrawOrderOf("zz") <= DrawOrderOf("ri") {
		t.Error("an unknown part type should sort in front of every known one")
	}
	if DrawOrderOf("hd") <= DrawOrderOf("ch") {
		t.Error("the head should draw over the shirt")
	}
}

// ─── rendering ──────────────────────────────────────────────────────────────

func TestRenderAvatarProducesAPNG(t *testing.T) {
	renderer := NewRenderer(loadStandardPack(t))

	data, err := renderer.RenderAvatar(Request{Figure: testFigure, Direction: 2})
	if err != nil {
		t.Fatalf("RenderAvatar: %v", err)
	}

	img, err := png.Decode(bytes.NewReader(data))
	if err != nil {
		t.Fatalf("decode: %v", err)
	}
	// The shirt spans 10 wide and the parts stack to the shirt's full height.
	if got := img.Bounds().Dx(); got != 10 {
		t.Errorf("width = %d, want 10", got)
	}
	if got := img.Bounds().Dy(); got < 12 {
		t.Errorf("height = %d, want at least the shirt's 12", got)
	}
}

func TestRenderAvatarReportsAFigureWithNoParts(t *testing.T) {
	renderer := NewRenderer(loadStandardPack(t))
	if _, err := renderer.RenderAvatar(Request{Figure: "zz-1-1", Direction: 2}); err == nil {
		t.Error("expected an error for a figure the pack cannot draw")
	}
}

func TestCompositeSizesTheCanvasToItsContents(t *testing.T) {
	img := Composite([]Placement{
		{Image: solid(4, 4, color.NRGBA{255, 0, 0, 255}), OffsetX: 2, OffsetY: 2},
		{Image: solid(4, 4, color.NRGBA{0, 255, 0, 255}), OffsetX: -2, OffsetY: 2},
	})
	// The two sit at x -2..2 and 2..6, so together they span eight pixels.
	if got := img.Bounds().Dx(); got != 8 {
		t.Errorf("width = %d, want 8", got)
	}
	if got := img.Bounds().Dy(); got != 4 {
		t.Errorf("height = %d, want 4", got)
	}
}

func TestCompositeMirrorsAboutTheOriginNotThePartsOwnWidth(t *testing.T) {
	// A part whose pixels sit to the avatar's right must land the same distance
	// to its left when mirrored. Reflecting about the part's own width instead
	// slides each part by however wide it happens to be, which pulls a figure
	// apart.
	upright := placedBounds(Placement{Image: solid(4, 4, color.NRGBA{}), OffsetX: -6})
	mirrored := placedBounds(Placement{Image: solid(4, 4, color.NRGBA{}), OffsetX: -6, Flip: true})

	if upright.Min.X != 6 || upright.Max.X != 10 {
		t.Fatalf("upright x = %d..%d, want 6..10", upright.Min.X, upright.Max.X)
	}
	if mirrored.Min.X != -9 || mirrored.Max.X != -5 {
		t.Errorf("mirrored x = %d..%d, want -9..-5", mirrored.Min.X, mirrored.Max.X)
	}
}

func TestPrepareTintsAndMirrors(t *testing.T) {
	src := image.NewNRGBA(image.Rect(0, 0, 2, 1))
	src.SetNRGBA(0, 0, color.NRGBA{255, 255, 255, 255})
	src.SetNRGBA(1, 0, color.NRGBA{128, 128, 128, 255})

	out := prepare(Placement{Image: src, Flip: true, Tint: 0xFF0000, HasTint: true})

	// Mirroring swaps the columns; the tint keeps red and drops the rest.
	left := color.NRGBAModel.Convert(out.At(0, 0)).(color.NRGBA)
	if left.R != 128 || left.G != 0 || left.B != 0 {
		t.Errorf("left pixel = %+v, want the mid-grey pixel tinted red", left)
	}
	right := color.NRGBAModel.Convert(out.At(1, 0)).(color.NRGBA)
	if right.R != 255 || right.G != 0 || right.B != 0 {
		t.Errorf("right pixel = %+v, want the white pixel tinted red", right)
	}
}

func TestPrepareKeepsTransparency(t *testing.T) {
	src := image.NewNRGBA(image.Rect(0, 0, 1, 1))
	src.SetNRGBA(0, 0, color.NRGBA{200, 100, 50, 0})

	out := prepare(Placement{Image: src, Tint: 0x00FF00, HasTint: true})
	if _, _, _, a := out.At(0, 0).RGBA(); a != 0 {
		t.Errorf("alpha = %d, want a transparent pixel to stay transparent", a)
	}
}

func TestLoadImageRefusesPathsOutsideThePack(t *testing.T) {
	root := writePack(t, map[string]SpriteEntry{
		"escape": {File: "../../etc/passwd"},
	})
	pack, err := LoadPack(root)
	if err != nil {
		t.Fatal(err)
	}
	if _, err := NewRenderer(pack).loadImage("../../etc/passwd"); err == nil {
		t.Error("a sprite path leaving the pack must be refused")
	}
}

// ─── badges ─────────────────────────────────────────────────────────────────

func TestParseBadgeCode(t *testing.T) {
	parts, err := ParseBadgeCode("b03120s13181t01014")
	if err != nil {
		t.Fatalf("ParseBadgeCode: %v", err)
	}
	if len(parts) != 3 {
		t.Fatalf("parts = %d, want 3", len(parts))
	}

	if parts[0] != (BadgePart{Base: true, ID: 3, Colour: 12, Position: 0}) {
		t.Errorf("base = %+v", parts[0])
	}
	if parts[1] != (BadgePart{ID: 13, Colour: 18, Position: 1}) {
		t.Errorf("symbol = %+v", parts[1])
	}
	// "t" continues the numbering past ninety-nine.
	if parts[2] != (BadgePart{ID: 101, Colour: 1, Position: 4}) {
		t.Errorf("high symbol = %+v", parts[2])
	}
}

func TestParseBadgeCodeRejectsMalformedCodes(t *testing.T) {
	for _, code := range []string{
		"",                                     // nothing at all
		"b0312",                                // a part short of its digits
		"x03120",                               // an unknown kind
		"b0312a",                               // a letter where a digit belongs
		"b03120s13181s01014s02023s03032s04041", // more parts than a badge holds
	} {
		if _, err := ParseBadgeCode(code); err == nil {
			t.Errorf("ParseBadgeCode(%q) should have failed", code)
		}
	}
}

// badgePack lays out a pack with badge artwork and a palette.
func badgePack(t *testing.T) (*Renderer, *BadgeParts) {
	t.Helper()
	root := writePack(t, map[string]SpriteEntry{
		"badgepart_base_3_12": {File: "badge/base3.png", Width: 39, Height: 39},
		"badgepart_13":        {File: "badge/symbol13.png", Width: 10, Height: 10},
		"badge_ACH_Login1":    {File: "badge/ach.png", Width: 40, Height: 40},
	})
	solidPNG(t, filepath.Join(root, "badge/base3.png"), 39, 39, color.NRGBA{0, 0, 200, 255})
	solidPNG(t, filepath.Join(root, "badge/symbol13.png"), 10, 10, color.NRGBA{255, 255, 255, 255})
	solidPNG(t, filepath.Join(root, "badge/ach.png"), 40, 40, color.NRGBA{0, 200, 0, 255})

	if err := os.WriteFile(filepath.Join(root, "badgeparts.xml"), []byte(
		`<badgeparts><color id="18">FFAA00</color></badgeparts>`), 0o644); err != nil {
		t.Fatal(err)
	}

	pack, err := LoadPack(root)
	if err != nil {
		t.Fatal(err)
	}
	badgeParts, err := LoadBadgeParts(root)
	if err != nil {
		t.Fatal(err)
	}
	return NewRenderer(pack), badgeParts
}

func TestRenderBadgeComposesBaseAndSymbol(t *testing.T) {
	renderer, badgeParts := badgePack(t)

	data, err := renderer.RenderBadge("b03120s13181", badgeParts)
	if err != nil {
		t.Fatalf("RenderBadge: %v", err)
	}

	img, err := png.Decode(bytes.NewReader(data))
	if err != nil {
		t.Fatal(err)
	}
	if img.Bounds().Dx() != BadgeSize || img.Bounds().Dy() != BadgeSize {
		t.Errorf("badge is %v, want %dx%d", img.Bounds().Size(), BadgeSize, BadgeSize)
	}

	// Position 1 is the top centre, where the symbol takes the palette colour.
	r, g, b, _ := img.At(BadgeSize/2, 6).RGBA()
	if r>>8 != 0xFF || g>>8 != 0xAA || b>>8 != 0x00 {
		t.Errorf("symbol pixel = %02X%02X%02X, want FFAA00", r>>8, g>>8, b>>8)
	}
	// A corner outside the symbol's cell shows the base.
	r, g, b, _ = img.At(2, 36).RGBA()
	if b>>8 != 200 || r>>8 != 0 {
		t.Errorf("base pixel = %02X%02X%02X, want the base colour", r>>8, g>>8, b>>8)
	}
}

func TestBadgePlacementFollowsTheGrid(t *testing.T) {
	small := image.Pt(9, 9)
	cases := map[int]image.Point{
		0: {2, 2},   // upper left
		4: {15, 15}, // centre
		8: {28, 28}, // lower right
	}
	for position, want := range cases {
		got := badgePlacement(BadgePart{ID: 1, Position: position}, small)
		if got != want {
			t.Errorf("position %d = %v, want %v", position, got, want)
		}
	}
}

func TestBadgePlacementCentresPartsTooBigForACell(t *testing.T) {
	// The editor centres full-size shapes rather than cramming them in a cell.
	got := badgePlacement(BadgePart{ID: 1, Position: 0}, image.Pt(39, 39))
	if got != (image.Point{}) {
		t.Errorf("oversized part at %v, want it centred at 0,0", got)
	}
}

func TestRenderBadgeReportsPartsThePackLacks(t *testing.T) {
	renderer, badgeParts := badgePack(t)
	if _, err := renderer.RenderBadge("b99990", badgeParts); err == nil {
		t.Error("expected an error for a badge whose parts are missing")
	}
}

func TestLoadBadgePartsToleratesAPackWithoutThem(t *testing.T) {
	parts, err := LoadBadgeParts(t.TempDir())
	if err != nil {
		t.Fatalf("a pack without badgeparts.xml should still load: %v", err)
	}
	if len(parts.Palette) != 0 {
		t.Errorf("palette = %v, want empty", parts.Palette)
	}
}

// ─── server ─────────────────────────────────────────────────────────────────

// serverOverPack puts one pack under an era directory and serves it.
func serverOverPack(t *testing.T, packRoot string) *Server {
	t.Helper()
	assets := t.TempDir()
	era := filepath.Join(assets, "modern")
	if err := os.Symlink(packRoot, era); err != nil {
		t.Fatal(err)
	}
	return NewServer(assets, "modern")
}

func TestServerRendersAnAvatar(t *testing.T) {
	server := serverOverPack(t, standardPack(t))

	rec := httptest.NewRecorder()
	server.Handler().ServeHTTP(rec,
		httptest.NewRequest(http.MethodGet, "/avatar.png?figure="+testFigure, nil))

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d: %s", rec.Code, rec.Body.String())
	}
	if got := rec.Header().Get("Content-Type"); got != "image/png" {
		t.Errorf("content type = %q", got)
	}
	if _, err := png.Decode(bytes.NewReader(rec.Body.Bytes())); err != nil {
		t.Errorf("body is not a PNG: %v", err)
	}
}

func TestServerRequiresAFigure(t *testing.T) {
	server := serverOverPack(t, standardPack(t))

	rec := httptest.NewRecorder()
	server.Handler().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/avatar", nil))

	if rec.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want 400", rec.Code)
	}
}

func TestServerScalesWithoutSmoothing(t *testing.T) {
	server := serverOverPack(t, standardPack(t))
	handler := server.Handler()

	once := httptest.NewRecorder()
	handler.ServeHTTP(once, httptest.NewRequest(http.MethodGet, "/avatar?figure="+testFigure, nil))
	twice := httptest.NewRecorder()
	handler.ServeHTTP(twice, httptest.NewRequest(http.MethodGet, "/avatar?figure="+testFigure+"&scale=2", nil))

	small, err := png.Decode(bytes.NewReader(once.Body.Bytes()))
	if err != nil {
		t.Fatal(err)
	}
	large, err := png.Decode(bytes.NewReader(twice.Body.Bytes()))
	if err != nil {
		t.Fatal(err)
	}
	if large.Bounds().Dx() != small.Bounds().Dx()*2 {
		t.Errorf("scaled width = %d, want %d", large.Bounds().Dx(), small.Bounds().Dx()*2)
	}
}

func TestServerRefusesAnAbsurdScale(t *testing.T) {
	server := serverOverPack(t, standardPack(t))
	handler := server.Handler()

	plain := httptest.NewRecorder()
	handler.ServeHTTP(plain, httptest.NewRequest(http.MethodGet, "/avatar?figure="+testFigure, nil))
	huge := httptest.NewRecorder()
	handler.ServeHTTP(huge, httptest.NewRequest(http.MethodGet, "/avatar?figure="+testFigure+"&scale=99", nil))

	if huge.Code != http.StatusOK {
		t.Fatalf("status = %d", huge.Code)
	}
	if huge.Body.Len() != plain.Body.Len() {
		t.Error("an out-of-range scale should fall back to drawing at one pixel per pixel")
	}
}

func TestServerRendersAHeadOnAndItsOwn(t *testing.T) {
	server := serverOverPack(t, standardPack(t))
	handler := server.Handler()

	full := httptest.NewRecorder()
	handler.ServeHTTP(full, httptest.NewRequest(http.MethodGet, "/avatar?figure="+testFigure, nil))
	head := httptest.NewRecorder()
	handler.ServeHTTP(head, httptest.NewRequest(http.MethodGet, "/avatar?figure="+testFigure+"&headonly=1", nil))

	if head.Code != http.StatusOK {
		t.Fatalf("status = %d: %s", head.Code, head.Body.String())
	}
	fullImg, _ := png.Decode(bytes.NewReader(full.Body.Bytes()))
	headImg, _ := png.Decode(bytes.NewReader(head.Body.Bytes()))
	if headImg.Bounds().Dy() >= fullImg.Bounds().Dy() {
		t.Errorf("head render is %v, expected shorter than the full figure %v",
			headImg.Bounds().Size(), fullImg.Bounds().Size())
	}
}

func TestServerAnswers304WhenTheBrowserHasTheImage(t *testing.T) {
	server := serverOverPack(t, standardPack(t))
	handler := server.Handler()
	url := "/avatar?figure=" + testFigure

	first := httptest.NewRecorder()
	handler.ServeHTTP(first, httptest.NewRequest(http.MethodGet, url, nil))
	etag := first.Header().Get("ETag")
	if etag == "" {
		t.Fatal("no ETag on the first response")
	}

	repeat := httptest.NewRequest(http.MethodGet, url, nil)
	repeat.Header.Set("If-None-Match", etag)
	second := httptest.NewRecorder()
	handler.ServeHTTP(second, repeat)

	if second.Code != http.StatusNotModified {
		t.Errorf("status = %d, want 304", second.Code)
	}
	if second.Body.Len() != 0 {
		t.Error("a 304 should carry no body")
	}
}

func TestServerServesABadgeCodeAndANamedBadge(t *testing.T) {
	renderer, _ := badgePack(t)
	server := serverOverPack(t, renderer.Pack().Root)
	handler := server.Handler()

	for _, name := range []string{"b03120s13181", "ACH_Login1"} {
		rec := httptest.NewRecorder()
		handler.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/badge/"+name+".png", nil))
		if rec.Code != http.StatusOK {
			t.Errorf("badge %q: status = %d: %s", name, rec.Code, rec.Body.String())
			continue
		}
		if _, err := png.Decode(bytes.NewReader(rec.Body.Bytes())); err != nil {
			t.Errorf("badge %q is not a PNG: %v", name, err)
		}
	}
}

func TestServerReportsAnUninstalledEra(t *testing.T) {
	server := serverOverPack(t, standardPack(t))

	rec := httptest.NewRecorder()
	server.Handler().ServeHTTP(rec,
		httptest.NewRequest(http.MethodGet, "/avatar?figure="+testFigure+"&era=classic", nil))

	if rec.Code != http.StatusServiceUnavailable {
		t.Errorf("status = %d, want 503 for an era with no artwork", rec.Code)
	}
}

func TestServerIsHealthy(t *testing.T) {
	server := serverOverPack(t, standardPack(t))
	rec := httptest.NewRecorder()
	server.Handler().ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/health", nil))
	if rec.Code != http.StatusOK {
		t.Errorf("status = %d", rec.Code)
	}
}

// ─── cache ──────────────────────────────────────────────────────────────────

func TestCacheEvictsTheLeastRecentlyUsed(t *testing.T) {
	cache := newPNGCache(2)
	cache.put("a", []byte("a"))
	cache.put("b", []byte("b"))

	// Touching "a" makes "b" the oldest, so "b" is what a third entry displaces.
	cache.get("a")
	cache.put("c", []byte("c"))

	if _, ok := cache.get("b"); ok {
		t.Error("b should have been evicted")
	}
	if _, ok := cache.get("a"); !ok {
		t.Error("a was used most recently and should have been kept")
	}
	if _, ok := cache.get("c"); !ok {
		t.Error("c was just added")
	}
}

func TestReloadDropsCachedPacks(t *testing.T) {
	server := serverOverPack(t, standardPack(t))
	handler := server.Handler()
	url := "/avatar?figure=" + testFigure

	first := httptest.NewRecorder()
	handler.ServeHTTP(first, httptest.NewRequest(http.MethodGet, url, nil))

	server.Reload()

	second := httptest.NewRecorder()
	handler.ServeHTTP(second, httptest.NewRequest(http.MethodGet, url, nil))
	if second.Code != http.StatusOK {
		t.Fatalf("status after reload = %d: %s", second.Code, second.Body.String())
	}
	if !bytes.Equal(first.Body.Bytes(), second.Body.Bytes()) {
		t.Error("the same request should render the same image after a reload")
	}
}

// ─── helpers ────────────────────────────────────────────────────────────────

func solid(w, h int, c color.NRGBA) image.Image {
	img := image.NewNRGBA(image.Rect(0, 0, w, h))
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			img.SetNRGBA(x, y, c)
		}
	}
	return img
}

func contains(haystack, needle string) bool {
	return bytes.Contains([]byte(haystack), []byte(needle))
}
