package swf

import (
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"time"
)

const packManifestPath = "/var/lib/habnut/swf/PACK_MANIFEST.json"

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

// Install unpacks a SWF archive from packPath into the swf directory.
func Install(packPath string) error {
	swfDir := filepath.Dir(packManifestPath)
	if err := os.MkdirAll(swfDir, 0755); err != nil {
		return err
	}
	if err := run("unzip", "-o", packPath, "-d", swfDir); err != nil {
		return fmt.Errorf("unpack failed: %w", err)
	}
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
