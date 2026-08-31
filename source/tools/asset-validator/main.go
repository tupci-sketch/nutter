package main

import (
	"archive/zip"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

// Manifest describes the expected structure of a SWF asset pack.
type Manifest struct {
	Version      string   `json:"version"`
	PackName     string   `json:"pack_name"`
	RequiredXML  []string `json:"required_xml"`
	RequiredSWF  []string `json:"required_swf"`
	RequiredJSON []string `json:"required_json"`
}

// Result holds findings from a single check.
type Result struct {
	Check   string
	Passed  bool
	Message string
}

func main() {
	if len(os.Args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: asset-validator <pack.zip> [manifest.json]")
		os.Exit(1)
	}

	packPath := os.Args[1]
	manifestPath := "PACK_MANIFEST.json"
	if len(os.Args) >= 3 {
		manifestPath = os.Args[2]
	}

	manifest, err := loadManifest(manifestPath)
	if err != nil {
		fmt.Fprintf(os.Stderr, "manifest load error: %v\n", err)
		os.Exit(1)
	}

	results, err := validate(packPath, manifest)
	if err != nil {
		fmt.Fprintf(os.Stderr, "validation error: %v\n", err)
		os.Exit(1)
	}

	failures := 0
	for _, r := range results {
		status := "PASS"
		if !r.Passed {
			status = "FAIL"
			failures++
		}
		fmt.Printf("[%s] %s: %s\n", status, r.Check, r.Message)
	}

	if failures > 0 {
		fmt.Fprintf(os.Stderr, "\n%d check(s) failed.\n", failures)
		os.Exit(1)
	}
	fmt.Printf("\nAll %d checks passed.\n", len(results))
}

func loadManifest(path string) (*Manifest, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var m Manifest
	return &m, json.Unmarshal(data, &m)
}

func validate(packPath string, manifest *Manifest) ([]Result, error) {
	r, err := zip.OpenReader(packPath)
	if err != nil {
		return nil, fmt.Errorf("cannot open pack: %w", err)
	}
	defer r.Close()

	// Index all file names in the ZIP.
	present := make(map[string]bool)
	for _, f := range r.File {
		present[f.Name] = true
		present[filepath.Base(f.Name)] = true
	}

	var results []Result

	// Check required XML files.
	for _, name := range manifest.RequiredXML {
		results = append(results, checkPresent(present, name, "required-xml"))
	}

	// Check required SWF files.
	for _, name := range manifest.RequiredSWF {
		results = append(results, checkPresent(present, name, "required-swf"))
	}

	// Check required JSON files.
	for _, name := range manifest.RequiredJSON {
		results = append(results, checkPresent(present, name, "required-json"))
	}

	// Check for placeholder content in XML files.
	for _, f := range r.File {
		if !strings.HasSuffix(f.Name, ".xml") {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			continue
		}
		buf := make([]byte, 4096)
		n, _ := rc.Read(buf)
		rc.Close()
		content := string(buf[:n])
		for _, marker := range placeholderMarkers {
			if strings.Contains(content, marker) {
				results = append(results, Result{
					Check:   "no-placeholder",
					Passed:  false,
					Message: fmt.Sprintf("%s contains placeholder marker %q", f.Name, marker),
				})
			}
		}
	}

	// Manifest version present.
	results = append(results, Result{
		Check:   "manifest-version",
		Passed:  manifest.Version != "",
		Message: fmt.Sprintf("manifest version = %q", manifest.Version),
	})

	return results, nil
}

func checkPresent(present map[string]bool, name, check string) Result {
	if present[name] || present[filepath.Base(name)] {
		return Result{Check: check, Passed: true, Message: name + " found"}
	}
	return Result{Check: check, Passed: false, Message: name + " MISSING"}
}

var placeholderMarkers = []string{
	"TODO", "FIXME", "PLACEHOLDER", "STUB", "NOT_IMPLEMENTED",
	"habbo.com", "habboo", "sulake",
}
