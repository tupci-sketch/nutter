package swf

import (
	"encoding/json"
	"fmt"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/habnut/launcher/internal/swfextract"
)

const (
	packManifestPath = "/var/lib/habnut/swf/PACK_MANIFEST.json"
	assetsBase       = "/var/lib/habnut/assets"
)

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
	swfDir := filepath.Join(filepath.Dir(packManifestPath), era)
	if err := os.MkdirAll(swfDir, 0755); err != nil {
		return err
	}

	fmt.Println("→ Unpacking SWF archive…")
	if err := run("unzip", "-o", packPath, "-d", swfDir); err != nil {
		return fmt.Errorf("unpack failed: %w", err)
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
func extractByCategory(swfDir, era string) (int, error) {
	total := 0
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
		return nil
	})
	if err != nil {
		return total, err
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

	data, err := json.MarshalIndent(combined, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(filepath.Join(eraRoot(era), "manifest.json"), data, 0644)
}

// copyAssetXMLs finds and copies furnidata.xml, figuremap.xml, figuredata.xml
// from the unpacked SWF tree to assetsBase so the client can fetch them.
func copyAssetXMLs(swfDir, era string) error {
	targets := map[string]bool{
		"furnidata.xml":  false,
		"figuremap.xml":  false,
		"figuredata.xml": false,
		"effectmap.xml":  false,
		"productdata.xml": false,
	}
	return filepath.WalkDir(swfDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		name := strings.ToLower(filepath.Base(path))
		if !targets[name] {
			return nil
		}
		targets[name] = true // mark found (don't copy duplicates)
		dest := filepath.Join(eraRoot(era), name)
		data, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		return os.WriteFile(dest, data, 0644)
	})
}

// Update replaces one era's pack with a new one, keeping config.
func Update(packPath, era string) error {
	return Install(packPath, era)
}

// Rebrand applies Habnut branding strings to all asset manifests.
func Rebrand(brandName string) error {
	swfDir := filepath.Dir(packManifestPath)
	// Replace any occurrence of the original vendor name in XML/JSON manifests
	if err := run("find", swfDir, "-name", "*.xml", "-exec",
		"sed", "-i", "s/Habbo/"+brandName+"/g", "{}", ";"); err != nil {
		return err
	}
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
	swfDir := filepath.Dir(packManifestPath)
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
	data, err := os.ReadFile(packManifestPath)
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
	return os.WriteFile(packManifestPath, data, 0644)
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

func run(name string, args ...string) error {
	cmd := exec.Command(name, args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}
