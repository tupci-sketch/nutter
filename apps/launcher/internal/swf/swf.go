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
	furniAssetsDir   = assetsBase + "/furniture"
	figureAssetsDir  = assetsBase + "/figure"
	roomAssetsDir    = assetsBase + "/room"
	effectAssetsDir  = assetsBase + "/effect"
)

// PackManifest describes an installed SWF asset pack.
type PackManifest struct {
	PackVersion  string    `json:"pack_version"`
	PackName     string    `json:"pack_name"`
	InstalledAt  time.Time `json:"installed_at"`
	AssetCount   int       `json:"asset_count"`
	BrandedAs    string    `json:"branded_as"`
	Integrity    string    `json:"integrity_sha256"`
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
	fmt.Printf("Assets:     %d\n", m.AssetCount)
	fmt.Printf("Installed:  %s\n", m.InstalledAt.Format("2006-01-02 15:04"))
	return nil
}

// Install unpacks a SWF archive from packPath, extracts all sprites to the
// assets directory, and copies the XML data files the client needs.
func Install(packPath string) error {
	swfDir := filepath.Dir(packManifestPath)
	if err := os.MkdirAll(swfDir, 0755); err != nil {
		return err
	}

	fmt.Println("→ Unpacking SWF archive…")
	if err := run("unzip", "-o", packPath, "-d", swfDir); err != nil {
		return fmt.Errorf("unpack failed: %w", err)
	}

	// Create asset output directories.
	for _, dir := range []string{furniAssetsDir, figureAssetsDir, roomAssetsDir, effectAssetsDir} {
		if err := os.MkdirAll(dir, 0755); err != nil {
			return err
		}
	}

	// Copy XML data files consumed by the client.
	if err := copyAssetXMLs(swfDir); err != nil {
		fmt.Fprintf(os.Stderr, "warn: copy XML data files: %v\n", err)
	}

	// Extract sprites from SWF files, routing by filename prefix.
	fmt.Println("→ Extracting sprites from SWF files…")
	spriteCount, err := extractByCategory(swfDir)
	if err != nil {
		fmt.Fprintf(os.Stderr, "warn: sprite extraction: %v\n", err)
	}
	fmt.Printf("→ Extracted %d sprites\n", spriteCount)

	count, _ := countFiles(swfDir)
	m := &PackManifest{
		PackVersion: "1.0.0",
		PackName:    filepath.Base(packPath),
		InstalledAt: time.Now(),
		AssetCount:  count,
		BrandedAs:   "Habnut",
	}
	return saveManifest(m)
}

// extractByCategory walks swfDir, extracts each SWF into the appropriate
// assets subdirectory based on naming conventions, and returns total sprite count.
func extractByCategory(swfDir string) (int, error) {
	total := 0
	err := filepath.WalkDir(swfDir, func(path string, d fs.DirEntry, err error) error {
		if err != nil || d.IsDir() {
			return err
		}
		if strings.ToLower(filepath.Ext(path)) != ".swf" {
			return nil
		}

		base := strings.TrimSuffix(strings.ToLower(filepath.Base(path)), ".swf")
		outDir := categoriseAsset(base)

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

	// Build a unified manifest.json in assetsBase combining all categories.
	if err := mergeManifests(); err != nil {
		fmt.Fprintf(os.Stderr, "warn: merge manifests: %v\n", err)
	}
	return total, nil
}

// categoriseAsset maps a SWF base name to the appropriate output directory.
func categoriseAsset(base string) string {
	switch {
	case strings.HasPrefix(base, "hh_human"):
		return figureAssetsDir
	case strings.HasPrefix(base, "hh_avatar_effects"):
		return effectAssetsDir
	case strings.HasPrefix(base, "room_") || strings.HasPrefix(base, "model_"):
		return roomAssetsDir
	default:
		return furniAssetsDir
	}
}

// mergeManifests reads manifest.json from each asset subdirectory and writes
// a combined manifest to assetsBase/manifest.json for the client to consume.
func mergeManifests() error {
	combined := &swfextract.Manifest{Sprites: make(map[string]swfextract.SpriteEntry)}

	for _, sub := range []struct{ dir, prefix string }{
		{furniAssetsDir, "furniture/"},
		{figureAssetsDir, "figure/"},
		{roomAssetsDir, "room/"},
		{effectAssetsDir, "effect/"},
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
	return os.WriteFile(filepath.Join(assetsBase, "manifest.json"), data, 0644)
}

// copyAssetXMLs finds and copies furnidata.xml, figuremap.xml, figuredata.xml
// from the unpacked SWF tree to assetsBase so the client can fetch them.
func copyAssetXMLs(swfDir string) error {
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
		dest := filepath.Join(assetsBase, name)
		data, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		return os.WriteFile(dest, data, 0644)
	})
}

// Update replaces the current pack with a new one, keeping config.
func Update(packPath string) error {
	return Install(packPath)
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
func Validate() error {
	swfDir := filepath.Dir(packManifestPath)
	required := []string{
		"gordon/PRODUCTION-201612152204-226667486/figuremap.xml",
		"gordon/PRODUCTION-201612152204-226667486/furnidata.xml",
	}
	for _, f := range required {
		if _, err := os.Stat(filepath.Join(swfDir, f)); err != nil {
			return fmt.Errorf("missing required asset: %s", f)
		}
	}
	return nil
}

// Rollback restores a backed-up pack.
func Rollback(backupPath string) error {
	return Install(backupPath)
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
