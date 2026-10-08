package swf

import (
	"archive/zip"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// Unpacking an asset pack.
//
// A pack is a zip somebody downloaded from a forum, so it is handled as
// untrusted input. It is also handled without shelling out: `unzip` is not on
// a default Linux Mint install and exists on no Windows machine, and the whole
// promise of a single executable is that it needs nothing beside it.

func writeZip(t *testing.T, entries map[string]string) string {
	t.Helper()

	path := filepath.Join(t.TempDir(), "pack.zip")
	f, err := os.Create(path)
	if err != nil {
		t.Fatalf("creating the archive: %v", err)
	}
	defer f.Close()

	w := zip.NewWriter(f)
	for name, body := range entries {
		e, err := w.Create(name)
		if err != nil {
			t.Fatalf("adding %s: %v", name, err)
		}
		if _, err := e.Write([]byte(body)); err != nil {
			t.Fatalf("writing %s: %v", name, err)
		}
	}
	if err := w.Close(); err != nil {
		t.Fatalf("closing the archive: %v", err)
	}
	return path
}

func TestUnpackingWritesEveryFile(t *testing.T) {
	archive := writeZip(t, map[string]string{
		"figuredata.xml":      "<figuredata/>",
		"gordon/hh_human.swf": "swf-bytes",
		"badges/ADM.png":      "png-bytes",
	})
	dest := t.TempDir()

	if err := unzipInto(archive, dest); err != nil {
		t.Fatalf("unzipInto: %v", err)
	}

	for name, want := range map[string]string{
		"figuredata.xml":      "<figuredata/>",
		"gordon/hh_human.swf": "swf-bytes",
		"badges/ADM.png":      "png-bytes",
	} {
		got, err := os.ReadFile(filepath.Join(dest, filepath.FromSlash(name)))
		if err != nil {
			t.Errorf("%s was not extracted: %v", name, err)
			continue
		}
		if string(got) != want {
			t.Errorf("%s = %q, want %q", name, got, want)
		}
	}
}

func TestUnpackingRefusesAnEntryThatEscapesTheDirectory(t *testing.T) {
	// A zip may name its entries anything, including a path that climbs out
	// of where it is being extracted. An extractor that joins them blindly
	// writes exactly where the archive tells it to.
	archive := writeZip(t, map[string]string{
		"../../escaped.txt": "should never be written",
	})
	dest := t.TempDir()

	err := unzipInto(archive, dest)
	if err == nil {
		t.Fatal("an entry pointing outside the pack directory was accepted")
	}
	if !strings.Contains(err.Error(), "outside") {
		t.Errorf("the error does not say what was wrong: %v", err)
	}

	if _, statErr := os.Stat(filepath.Join(filepath.Dir(dest), "escaped.txt")); statErr == nil {
		t.Error("the entry was written outside the pack directory")
	}
}

func TestUnpackingOverwritesAPreviousPack(t *testing.T) {
	dest := t.TempDir()
	if err := os.WriteFile(filepath.Join(dest, "figuredata.xml"), []byte("old"), 0o644); err != nil {
		t.Fatalf("seeding: %v", err)
	}

	archive := writeZip(t, map[string]string{"figuredata.xml": "new"})
	if err := unzipInto(archive, dest); err != nil {
		t.Fatalf("unzipInto: %v", err)
	}

	got, _ := os.ReadFile(filepath.Join(dest, "figuredata.xml"))
	if string(got) != "new" {
		t.Errorf("reinstalling a pack left the old file: %q", got)
	}
}

func TestUnpackingSaysWhichArchiveFailed(t *testing.T) {
	err := unzipInto(filepath.Join(t.TempDir(), "nothing-here.zip"), t.TempDir())
	if err == nil {
		t.Fatal("a missing archive was accepted")
	}
	if !strings.Contains(err.Error(), "nothing-here.zip") {
		t.Errorf("the error does not name the archive: %v", err)
	}
}

func TestRebrandingRewritesEveryManifest(t *testing.T) {
	root := t.TempDir()
	if err := os.MkdirAll(filepath.Join(root, "nested"), 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	for name, body := range map[string]string{
		"furnidata.xml":         `<furnidata name="Habbo Chair"/>`,
		"nested/figuredata.xml": `<figuredata vendor="Habbo"/>`,
		"left-alone.json":       `{"name": "Habbo"}`,
	} {
		if err := os.WriteFile(filepath.Join(root, filepath.FromSlash(name)),
			[]byte(body), 0o644); err != nil {
			t.Fatalf("writing %s: %v", name, err)
		}
	}

	changed, err := replaceInXML(root, "Habbo", "Habnut")
	if err != nil {
		t.Fatalf("replaceInXML: %v", err)
	}
	if changed != 2 {
		t.Errorf("changed %d files, want the two XML ones", changed)
	}

	got, _ := os.ReadFile(filepath.Join(root, "furnidata.xml"))
	if strings.Contains(string(got), "Habbo") {
		t.Errorf("the name survived rebranding: %q", got)
	}
	nested, _ := os.ReadFile(filepath.Join(root, "nested", "figuredata.xml"))
	if strings.Contains(string(nested), "Habbo") {
		t.Errorf("a nested manifest was missed: %q", nested)
	}
}

func TestRebrandingAMissingDirectoryIsNotAnError(t *testing.T) {
	changed, err := replaceInXML(filepath.Join(t.TempDir(), "never-created"), "a", "b")
	if err != nil {
		t.Errorf("rebranding before a pack is installed should do nothing, not fail: %v", err)
	}
	if changed != 0 {
		t.Errorf("changed %d files in a directory that does not exist", changed)
	}
}

func TestTheAssetRootsMoveTogether(t *testing.T) {
	packBefore, assetsBefore := Roots()
	t.Cleanup(func() { SetRoots(packBefore, assetsBefore) })

	SetRoots("/tmp/example/swf", "/tmp/example/assets")

	pack, assets := Roots()
	if pack != "/tmp/example/swf" || assets != "/tmp/example/assets" {
		t.Errorf("Roots() = %q, %q after SetRoots", pack, assets)
	}
	if !strings.HasPrefix(packManifest(), "/tmp/example/swf") {
		t.Errorf("the manifest did not move with the pack root: %s", packManifest())
	}
}

func TestAnInstalledHotelStillUsesTheSystemPaths(t *testing.T) {
	pack, assets := Roots()
	// The default has to stay where an installed hotel's services look, or an
	// upgrade would quietly start reading an empty directory.
	if pack != defaultPackRoot || assets != defaultAssetsBase {
		t.Errorf("Roots() = %q, %q; want the system paths by default", pack, assets)
	}
}

func TestInstallingFromADirectoryNeedsNoZipStep(t *testing.T) {
	// A pack fetched with a downloader arrives as a directory. Requiring it to
	// be zipped first would be a step that exists only to be undone.
	src := t.TempDir()
	if err := os.MkdirAll(filepath.Join(src, "gordon"), 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	for name, body := range map[string]string{
		"figuredata.xml":      "<figuredata/>",
		"gordon/hh_human.swf": "swf-bytes",
	} {
		if err := os.WriteFile(filepath.Join(src, filepath.FromSlash(name)),
			[]byte(body), 0o644); err != nil {
			t.Fatalf("writing %s: %v", name, err)
		}
	}

	dest := t.TempDir()
	if err := copyTree(src, dest); err != nil {
		t.Fatalf("copyTree: %v", err)
	}

	for _, name := range []string{"figuredata.xml", "gordon/hh_human.swf"} {
		if _, err := os.Stat(filepath.Join(dest, filepath.FromSlash(name))); err != nil {
			t.Errorf("%s was not copied: %v", name, err)
		}
	}
}

func TestCopyingAPackKeepsItsShape(t *testing.T) {
	src := t.TempDir()
	deep := filepath.Join(src, "dcr", "hof_furni", "icons")
	if err := os.MkdirAll(deep, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(deep, "chair.png"), []byte("x"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}

	dest := t.TempDir()
	if err := copyTree(src, dest); err != nil {
		t.Fatalf("copyTree: %v", err)
	}

	if _, err := os.Stat(filepath.Join(dest, "dcr", "hof_furni", "icons", "chair.png")); err != nil {
		t.Errorf("the nested path was flattened or lost: %v", err)
	}
}

func TestTheDataFilesReachWhereTheClientLooks(t *testing.T) {
	// The check here was inverted, so this copied nothing at all: every file
	// was skipped on the grounds that it had not been found yet. A pack could
	// install "successfully" and the client would have no furnidata, no
	// figuredata and no figuremap — nothing to draw an avatar or a chair from.
	packBefore, assetsBefore := Roots()
	t.Cleanup(func() { SetRoots(packBefore, assetsBefore) })

	root := t.TempDir()
	SetRoots(filepath.Join(root, "swf"), filepath.Join(root, "assets"))

	// A pack that scatters them, the way real ones do.
	pack := filepath.Join(root, "swf", "classic")
	for name, body := range map[string]string{
		"gamedata/furnidata.xml":   "<furnidata/>",
		"gamedata/figuredata.xml":  "<figuredata/>",
		"figuremap.xml":            "<figuremap/>",
		"gamedata/productdata.xml": "<productdata/>",
	} {
		full := filepath.Join(pack, filepath.FromSlash(name))
		if err := os.MkdirAll(filepath.Dir(full), 0o755); err != nil {
			t.Fatalf("mkdir: %v", err)
		}
		if err := os.WriteFile(full, []byte(body), 0o644); err != nil {
			t.Fatalf("writing %s: %v", name, err)
		}
	}

	if err := copyAssetXMLs(pack, "classic"); err != nil {
		t.Fatalf("copyAssetXMLs: %v", err)
	}

	// The client reads these from one place, whatever the pack's own layout.
	for _, name := range []string{"furnidata.xml", "figuredata.xml", "figuremap.xml", "productdata.xml"} {
		if _, err := os.Stat(filepath.Join(root, "assets", "classic", name)); err != nil {
			t.Errorf("%s never reached the client: %v", name, err)
		}
	}
}

func TestAPackMissingItsDataFilesSaysWhich(t *testing.T) {
	packBefore, assetsBefore := Roots()
	t.Cleanup(func() { SetRoots(packBefore, assetsBefore) })

	root := t.TempDir()
	SetRoots(filepath.Join(root, "swf"), filepath.Join(root, "assets"))

	pack := filepath.Join(root, "swf", "classic")
	if err := os.MkdirAll(pack, 0o755); err != nil {
		t.Fatalf("mkdir: %v", err)
	}
	if err := os.WriteFile(filepath.Join(pack, "furnidata.xml"), []byte("<f/>"), 0o644); err != nil {
		t.Fatalf("write: %v", err)
	}

	err := copyAssetXMLs(pack, "classic")
	if err == nil {
		t.Fatal("a pack with no figuredata installed without complaint")
	}
	// Silence here would look like a bug in the hotel rather than a gap in
	// the pack, so the message has to name what is missing.
	for _, want := range []string{"figuredata.xml", "figuremap.xml"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("the error does not mention %s: %v", want, err)
		}
	}
}

func TestOnlyTheFirstOfEachDataFileIsTaken(t *testing.T) {
	packBefore, assetsBefore := Roots()
	t.Cleanup(func() { SetRoots(packBefore, assetsBefore) })

	root := t.TempDir()
	SetRoots(filepath.Join(root, "swf"), filepath.Join(root, "assets"))

	pack := filepath.Join(root, "swf", "classic")
	for _, dir := range []string{"a", "b"} {
		if err := os.MkdirAll(filepath.Join(pack, dir), 0o755); err != nil {
			t.Fatalf("mkdir: %v", err)
		}
	}
	for name, body := range map[string]string{
		"a/figuredata.xml": "first",
		"b/figuredata.xml": "second",
		"furnidata.xml":    "<f/>",
		"figuremap.xml":    "<m/>",
	} {
		if err := os.WriteFile(filepath.Join(pack, filepath.FromSlash(name)),
			[]byte(body), 0o644); err != nil {
			t.Fatalf("writing %s: %v", name, err)
		}
	}

	if err := copyAssetXMLs(pack, "classic"); err != nil {
		t.Fatalf("copyAssetXMLs: %v", err)
	}

	got, _ := os.ReadFile(filepath.Join(root, "assets", "classic", "figuredata.xml"))
	if string(got) != "first" {
		t.Errorf("figuredata.xml = %q; a later duplicate overwrote the first", got)
	}
}

// A build taken from the live client names its furniture data
// furnidata_xml.xml; it has to reach the client as furnidata.xml.
func TestLiveClientFurnidataNameIsRecognised(t *testing.T) {
	pack := t.TempDir()
	for name, body := range map[string]string{
		"gamedata/furnidata_xml.xml": "<furnidata/>",
		"gamedata/figuredata.xml":    "<figuredata/>",
		"figuremap.xml":              "<map/>",
	} {
		p := filepath.Join(pack, name)
		if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
			t.Fatal(err)
		}
		if err := os.WriteFile(p, []byte(body), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	oldPack, oldAssets := Roots()
	defer SetRoots(oldPack, oldAssets)
	SetRoots(t.TempDir(), t.TempDir())

	if err := copyAssetXMLs(pack, EraModern); err != nil {
		t.Fatalf("pack was refused: %v", err)
	}
	got, err := os.ReadFile(filepath.Join(eraRoot(EraModern), "furnidata.xml"))
	if err != nil || string(got) != "<furnidata/>" {
		t.Fatalf("furnidata.xml not written from furnidata_xml.xml: %q %v", got, err)
	}
}
