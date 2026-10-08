package swf

import (
	"archive/zip"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/habnut/launcher/internal/swfextract"
)

// Where an installed hotel keeps its artwork.
const (
	defaultPackRoot   = "/var/lib/habnut/swf"
	defaultAssetsBase = "/var/lib/habnut/assets"
)

// Where this process keeps artwork.
//
// An installed hotel uses the system paths above. A hotel somebody is running
// on their own laptop keeps everything in one directory under their home, and
// nothing about it should need root — so the root is relocatable rather than
// a constant. `habnutctl dev` points these at its own directory before it
// unpacks anything.
var (
	packRoot   = defaultPackRoot
	assetsBase = defaultAssetsBase
)

// SetRoots points the asset commands at a different hotel.
//
// Both paths move together: a pack and the sprites extracted from it belong to
// the same hotel, and splitting them is how you end up with a manifest that
// describes artwork that is not there.
func SetRoots(packDir, assetsDir string) {
	if packDir != "" {
		packRoot = packDir
	}
	if assetsDir != "" {
		assetsBase = assetsDir
	}
}

// Roots reports where artwork is being read from and written to.
func Roots() (packDir, assetsDir string) { return packRoot, assetsBase }

// packManifest is the file describing what is installed.
func packManifest() string { return filepath.Join(packRoot, "PACK_MANIFEST.json") }

// Eras a pack can be installed under. Both describe the same hotel and differ
// only in artwork, so a player can switch between them without leaving the room
// they are standing in.
const (
	EraClassic = "classic"
	EraModern  = "modern"
)

// ValidEra reports whether name is an era a pack may be installed under.
func ValidEra(name string) bool {
	return name == EraClassic || name == EraModern
}

// eraRoot is the directory holding one era's extracted artwork.
func eraRoot(era string) string { return filepath.Join(assetsBase, era) }

// Asset categories within an era.
func furniAssetsDir(era string) string  { return filepath.Join(eraRoot(era), "furniture") }
func figureAssetsDir(era string) string { return filepath.Join(eraRoot(era), "figure") }
func roomAssetsDir(era string) string   { return filepath.Join(eraRoot(era), "room") }
func effectAssetsDir(era string) string { return filepath.Join(eraRoot(era), "effect") }

// PackManifest describes the installed SWF asset packs.
type PackManifest struct {
	PackVersion string             `json:"pack_version"`
	PackName    string             `json:"pack_name"`
	InstalledAt time.Time          `json:"installed_at"`
	AssetCount  int                `json:"asset_count"`
	BrandedAs   string             `json:"branded_as"`
	Integrity   string             `json:"integrity_sha256"`
	Eras        map[string]EraInfo `json:"eras,omitempty"`
}

// EraInfo records what is installed for one visual era.
type EraInfo struct {
	PackName    string    `json:"pack_name"`
	InstalledAt time.Time `json:"installed_at"`
	SpriteCount int       `json:"sprite_count"`
	AssetCount  int       `json:"asset_count"`
}

// Status prints the current SWF pack status.
func Status() error {
	m, err := loadManifest()
	if err != nil {
		fmt.Println("No SWF pack installed.")
		return nil
	}
	fmt.Printf("Pack:       %s v%s\n", m.PackName, m.PackVersion)
	fmt.Printf("Branded as: %s\n", m.BrandedAs)

	if len(m.Eras) == 0 {
		fmt.Println("Eras:       none installed")
		return nil
	}
	fmt.Println("Eras:")
	for _, era := range []string{EraClassic, EraModern} {
		info, ok := m.Eras[era]
		if !ok {
			fmt.Printf("  %-8s not installed\n", era)
			continue
		}
		fmt.Printf("  %-8s %d sprites from %s (%s)\n",
			era, info.SpriteCount, info.PackName, info.InstalledAt.Format("2006-01-02 15:04"))
	}
	return nil
}

// Install unpacks a SWF archive from packPath, extracts all sprites into the
// given era's asset directory, and copies the XML data files the client needs.
//
// Installing one era leaves the other untouched, so a hotel can serve both and
// let each player choose.
func Install(packPath, era string) error {
	if !ValidEra(era) {
		return fmt.Errorf("unknown era %q: expected %s or %s", era, EraClassic, EraModern)
	}
	swfDir := filepath.Join(packRoot, era)
	if err := os.MkdirAll(swfDir, 0755); err != nil {
		return err
	}

	info, err := os.Stat(packPath)
	if err != nil {
		return fmt.Errorf("cannot read %s: %w", packPath, err)
	}

	if info.IsDir() {
		// A pack fetched with a downloader is a directory, not an archive.
		// Requiring it to be zipped first would be a step that exists only to
		// be undone a moment later.
		fmt.Println("→ Copying asset directory…")
		if err := copyTree(packPath, swfDir); err != nil {
			return fmt.Errorf("copy failed: %w", err)
		}
	} else {
		fmt.Println("→ Unpacking SWF archive…")
		if err := unzipInto(packPath, swfDir); err != nil {
			return fmt.Errorf("unpack failed: %w", err)
		}
	}

	// Create asset output directories.
	for _, dir := range []string{
		furniAssetsDir(era), figureAssetsDir(era), roomAssetsDir(era), effectAssetsDir(era),
	} {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return err
		}
	}

	// Copy XML data files consumed by the client.
	if err := copyAssetXMLs(swfDir, era); err != nil {
		fmt.Fprintf(os.Stderr, "warn: copy XML data files: %v\n", err)
	}

	// Extract sprites from SWF files, routing by filename prefix.
	fmt.Println("→ Extracting sprites from SWF files…")
	spriteCount, err := extractByCategory(swfDir, era)
	if err != nil {
		fmt.Fprintf(os.Stderr, "warn: sprite extraction: %v\n", err)
	}
	fmt.Printf("→ Extracted %d sprites\n", spriteCount)

	count, _ := countFiles(swfDir)
	m, err := loadManifest()
	if err != nil {
		m = &PackManifest{PackVersion: "1.0.0", BrandedAs: "Habnut", Eras: map[string]EraInfo{}}
	}
	if m.Eras == nil {
		m.Eras = map[string]EraInfo{}
	}
	m.PackName = filepath.Base(packPath)
	m.InstalledAt = time.Now()
	m.AssetCount = count
	m.Eras[era] = EraInfo{
		PackName:    filepath.Base(packPath),
		InstalledAt: time.Now(),
		SpriteCount: spriteCount,
		AssetCount:  count,
	}
	return saveManifest(m)
}

// extractByCategory walks swfDir, extracts each SWF into the appropriate
// assets subdirectory based on naming conventions, and returns total sprite count.
//
// Extracting a SWF writes its images and returns what it found, but writes no
// manifest; the merge below reads one from each directory, so none was ever
// there to read. A full build extracted every sprite and listed none, and the
// client could draw nothing. The sprites are collected here and each
// directory's manifest written once, at the end, with all of them.
func extractByCategory(swfDir, era string) (int, error) {
	total := 0
	collected := map[string]*swfextract.Manifest{}
	err := filepath.WalkDir(swfDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		if strings.ToLower(filepath.Ext(path)) != ".swf" {
			return nil
		}

		base := strings.TrimSuffix(strings.ToLower(filepath.Base(path)), ".swf")
		outDir := categoriseAsset(base, era)

		m, err := swfextract.Extract(path, outDir)
		if err != nil {
			fmt.Fprintf(os.Stderr, "warn: %s: %v\n", base, err)
			return nil
		}
		total += len(m.Sprites)
		dirManifest := collected[outDir]
		if dirManifest == nil {
			dirManifest = &swfextract.Manifest{Sprites: make(map[string]swfextract.SpriteEntry)}
			collected[outDir] = dirManifest
		}
		for name, entry := range m.Sprites {
			dirManifest.Sprites[name] = entry
		}
		return nil
	})
	if err != nil {
		return total, err
	}

	for dir, m := range collected {
		data, err := json.Marshal(m)
		if err != nil {
			return total, err
		}
		if err := os.WriteFile(filepath.Join(dir, "manifest.json"), data, 0o644); err != nil {
			return total, err
		}
	}

	// Build a unified manifest.json for this era combining all categories.
	if err := mergeManifests(era); err != nil {
		fmt.Fprintf(os.Stderr, "warn: merge manifests: %v\n", err)
	}
	return total, nil
}

// categoriseAsset maps a SWF base name to the appropriate output directory.
func categoriseAsset(base, era string) string {
	switch {
	case strings.HasPrefix(base, "hh_human"):
		return figureAssetsDir(era)
	case strings.HasPrefix(base, "hh_avatar_effects"):
		return effectAssetsDir(era)
	case strings.HasPrefix(base, "room_") || strings.HasPrefix(base, "model_"):
		return roomAssetsDir(era)
	default:
		return furniAssetsDir(era)
	}
}

// mergeManifests reads manifest.json from each asset subdirectory and writes
// a combined manifest to assetsBase/manifest.json for the client to consume.
func mergeManifests(era string) error {
	combined := &swfextract.Manifest{Sprites: make(map[string]swfextract.SpriteEntry)}

	for _, sub := range []struct{ dir, prefix string }{
		{furniAssetsDir(era), "furniture/"},
		{figureAssetsDir(era), "figure/"},
		{roomAssetsDir(era), "room/"},
		{effectAssetsDir(era), "effect/"},
		{hofAssetsDir(era), "hof/"},
	} {
		mPath := filepath.Join(sub.dir, "manifest.json")
		data, err := os.ReadFile(mPath)
		if err != nil {
			continue
		}
		var m swfextract.Manifest
		if err := json.Unmarshal(data, &m); err != nil {
			continue
		}
		for name, entry := range m.Sprites {
			entry.File = sub.prefix + entry.File
			combined.Sprites[name] = entry
		}
	}

	return writeJSONAtomic(filepath.Join(eraRoot(era), "manifest.json"), combined)
}

// copyAssetXMLs finds and copies furnidata.xml, figuremap.xml, figuredata.xml
// from the unpacked SWF tree to assetsBase so the client can fetch them.
// The data files the client reads straight out of an era's directory.
//
// furnidata, figuredata and figuremap are the three it cannot draw without:
// they say what each piece of furniture is, what an avatar is made of, and
// which sprite file each body part lives in. The other two are read by the
// hotel rather than the client.
var assetDataFiles = []string{
	"furnidata.xml",
	"figuremap.xml",
	"figuredata.xml",
	"effectmap.xml",
	"productdata.xml",
}

// dataFileAliases are other names packs give the same data files. A build
// taken from the live client calls furnidata "furnidata_xml.xml"; a pack like
// that was turned away as having no furniture data at all.
var dataFileAliases = map[string]string{
	"furnidata_xml.xml": "furnidata.xml",
}

// copyAssetXMLs lifts the data files out of a pack to where the client looks.
//
// A pack puts them wherever it likes — some under gamedata/, some at the root
// — and the client reads them from one place, so they are found by name and
// copied up. The first of each wins; a pack that ships two figuredata.xml has
// one that is current and one that is a leftover, and the walk order is as
// good a guess as any.
func copyAssetXMLs(swfDir, era string) error {
	wanted := make(map[string]bool, len(assetDataFiles))
	for _, name := range assetDataFiles {
		wanted[name] = true
	}
	found := make(map[string]bool, len(assetDataFiles))

	if err := os.MkdirAll(eraRoot(era), 0o755); err != nil {
		return err
	}

	err := filepath.WalkDir(swfDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}

		name := strings.ToLower(filepath.Base(path))
		if canonical, ok := dataFileAliases[name]; ok {
			name = canonical
		}
		if !wanted[name] || found[name] {
			return nil
		}

		data, readErr := os.ReadFile(path)
		if readErr != nil {
			return readErr
		}
		if writeErr := os.WriteFile(filepath.Join(eraRoot(era), name), data, 0o644); writeErr != nil {
			return writeErr
		}

		found[name] = true
		return nil
	})
	if err != nil {
		return err
	}

	// The three the client cannot draw without are worth naming when absent:
	// a pack missing one renders nothing, and silence would look like a bug
	// in the hotel rather than a gap in the pack.
	var missing []string
	for _, name := range []string{"furnidata.xml", "figuredata.xml", "figuremap.xml"} {
		if !found[name] {
			missing = append(missing, name)
		}
	}
	if len(missing) > 0 {
		return fmt.Errorf("this pack has no %s, so the client will draw nothing",
			strings.Join(missing, " or "))
	}

	return nil
}

// Update replaces one era's pack with a new one, keeping config.
func Update(packPath, era string) error {
	return Install(packPath, era)
}

// Rebrand applies this hotel's own name to the manifests inside a pack.
//
// Done in Go rather than by shelling out to find and sed: those exist on a
// Linux server and on neither Windows nor a stripped-down container, and the
// whole point of a single executable is that it needs nothing installed
// beside it.
func Rebrand(brandName string) error {
	replaced, err := replaceInXML(packRoot, "Habbo", brandName)
	if err != nil {
		return err
	}
	fmt.Printf("→ Rebranded %d manifest files\n", replaced)

	m, err := loadManifest()
	if err != nil {
		return err
	}
	m.BrandedAs = brandName
	return saveManifest(m)
}

// Validate checks that every required asset is present.
// Validate checks that each installed era has the data files the client needs
// and at least one extracted sprite.
func Validate() error {
	m, err := loadManifest()
	if err != nil {
		return fmt.Errorf("no pack installed")
	}
	if len(m.Eras) == 0 {
		return fmt.Errorf("no era installed: run 'habnutctl swf install <pack> --era classic|modern'")
	}

	for era := range m.Eras {
		root := eraRoot(era)
		for _, name := range []string{"manifest.json", "figuredata.xml", "figuremap.xml", "furnidata.xml"} {
			if _, err := os.Stat(filepath.Join(root, name)); err != nil {
				return fmt.Errorf("era %s is missing %s", era, name)
			}
		}

		data, err := os.ReadFile(filepath.Join(root, "manifest.json"))
		if err != nil {
			return fmt.Errorf("era %s: read manifest: %w", era, err)
		}
		var parsed swfextract.Manifest
		if err := json.Unmarshal(data, &parsed); err != nil {
			return fmt.Errorf("era %s: manifest is not valid JSON: %w", era, err)
		}
		if len(parsed.Sprites) == 0 {
			return fmt.Errorf("era %s extracted no sprites", era)
		}
	}
	return nil
}

// InstalledEras lists the eras that currently have artwork installed.
func InstalledEras() []string {
	m, err := loadManifest()
	if err != nil || len(m.Eras) == 0 {
		return nil
	}
	var out []string
	for _, era := range []string{EraClassic, EraModern} {
		if _, ok := m.Eras[era]; ok {
			out = append(out, era)
		}
	}
	return out
}

// Rollback restores a backed-up pack into one era.
func Rollback(backupPath, era string) error {
	return Install(backupPath, era)
}

// AddCustom copies a custom asset into the pack directory.
func AddCustom(assetPath, targetSubPath string) error {
	swfDir := packRoot
	dest := filepath.Join(swfDir, targetSubPath)
	if err := os.MkdirAll(filepath.Dir(dest), 0755); err != nil {
		return err
	}
	data, err := os.ReadFile(assetPath)
	if err != nil {
		return err
	}
	return os.WriteFile(dest, data, 0644)
}

func loadManifest() (*PackManifest, error) {
	data, err := os.ReadFile(packManifest())
	if err != nil {
		return nil, err
	}
	var m PackManifest
	return &m, json.Unmarshal(data, &m)
}

func saveManifest(m *PackManifest) error {
	data, err := json.MarshalIndent(m, "", "  ")
	if err != nil {
		return err
	}
	if err := os.MkdirAll(packRoot, 0o755); err != nil {
		return err
	}
	return os.WriteFile(packManifest(), data, 0644)
}

func countFiles(dir string) (int, error) {
	count := 0
	err := filepath.Walk(dir, func(_ string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if !info.IsDir() {
			count++
		}
		return nil
	})
	return count, err
}

// unzipInto extracts an archive, refusing any entry that would escape dest.
//
// An asset pack is a file somebody downloaded from a forum. A zip can name its
// entries anything it likes, including ../../etc/something, and an extractor
// that joins those paths blindly will write exactly where it is told.
func unzipInto(archivePath, dest string) error {
	reader, err := zip.OpenReader(archivePath)
	if err != nil {
		return fmt.Errorf("open %s: %w", archivePath, err)
	}
	defer reader.Close()

	if err := os.MkdirAll(dest, 0o755); err != nil {
		return err
	}

	root, err := filepath.Abs(dest)
	if err != nil {
		return err
	}

	for _, entry := range reader.File {
		target := filepath.Join(root, filepath.FromSlash(entry.Name))

		rel, err := filepath.Rel(root, target)
		if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(os.PathSeparator)) {
			return fmt.Errorf("%s: entry %q would write outside the pack directory",
				filepath.Base(archivePath), entry.Name)
		}

		if entry.FileInfo().IsDir() {
			if err := os.MkdirAll(target, 0o755); err != nil {
				return err
			}
			continue
		}

		// A symlink in a pack could point anywhere; packs do not need them.
		if entry.Mode()&os.ModeSymlink != 0 {
			continue
		}

		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return err
		}
		if err := writeZipEntry(entry, target); err != nil {
			return err
		}
	}

	return nil
}

func writeZipEntry(entry *zip.File, target string) error {
	src, err := entry.Open()
	if err != nil {
		return fmt.Errorf("read %s: %w", entry.Name, err)
	}
	defer src.Close()

	dst, err := os.OpenFile(target, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o644)
	if err != nil {
		return err
	}
	defer dst.Close()

	if _, err := io.Copy(dst, src); err != nil {
		return fmt.Errorf("write %s: %w", target, err)
	}
	return nil
}

// copyTree copies a directory into dest, keeping its shape.
//
// Symlinks are skipped: a pack has no use for one, and following a link out of
// the tree would copy whatever it points at into the hotel.
func copyTree(src, dest string) error {
	root, err := filepath.Abs(src)
	if err != nil {
		return err
	}

	return filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}

		rel, err := filepath.Rel(root, path)
		if err != nil {
			return err
		}
		if rel == "." {
			return os.MkdirAll(dest, 0o755)
		}

		target := filepath.Join(dest, rel)

		if d.IsDir() {
			return os.MkdirAll(target, 0o755)
		}
		if d.Type()&os.ModeSymlink != 0 {
			return nil
		}

		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return err
		}
		return copyFile(path, target)
	})
}

func copyFile(src, dest string) error {
	in, err := os.Open(src)
	if err != nil {
		return err
	}
	defer in.Close()

	out, err := os.OpenFile(dest, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o644)
	if err != nil {
		return err
	}
	defer out.Close()

	_, err = io.Copy(out, in)
	return err
}

// replaceInXML rewrites a string through every XML file under root, returning
// how many files it changed.
func replaceInXML(root, from, to string) (int, error) {
	if from == "" || from == to {
		return 0, nil
	}

	changed := 0
	err := filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
		if err != nil {
			return err
		}
		if d.IsDir() || !strings.EqualFold(filepath.Ext(path), ".xml") {
			return nil
		}

		body, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		if !strings.Contains(string(body), from) {
			return nil
		}

		info, err := d.Info()
		if err != nil {
			return err
		}
		if err := os.WriteFile(path, []byte(strings.ReplaceAll(string(body), from, to)),
			info.Mode().Perm()); err != nil {
			return err
		}
		changed++
		return nil
	})

	if errors.Is(err, fs.ErrNotExist) {
		return changed, nil
	}
	return changed, err
}

// AssetsRoot is the directory holding every installed era's artwork.
func AssetsRoot() string { return assetsBase }

// EraRoot is the directory holding one era's artwork.
func EraRoot(era string) string { return eraRoot(era) }
