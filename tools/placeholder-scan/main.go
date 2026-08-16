package main

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strings"
)

// Finding represents a single prohibited pattern match.
type Finding struct {
	File    string
	Line    int
	Pattern string
	Text    string
}

// placeholderPatterns must not appear anywhere in committed code. Each marks
// work that was left unfinished or a credential that was hard-coded.
var placeholderPatterns = []*regexp.Regexp{
	// Stub/placeholder code markers
	regexp.MustCompile(`(?i)\bTODO\b`),
	regexp.MustCompile(`(?i)\bFIXME\b`),
	regexp.MustCompile(`(?i)\bHACK\b`),
	regexp.MustCompile(`(?i)\bNOTIMPLEMENTED\b`),
	regexp.MustCompile(`(?i)throw new NotImplementedException`),
	regexp.MustCompile(`(?i)throw new UnsupportedOperationException\(\)`),
	regexp.MustCompile(`(?i)// stub`),
	regexp.MustCompile(`(?i)// placeholder`),

	// Hard-coded credentials / unsafe defaults
	regexp.MustCompile(`password\s*=\s*["']password["']`),
	regexp.MustCompile(`password\s*=\s*["']123`),
	regexp.MustCompile(`secret\s*=\s*["']secret["']`),
	regexp.MustCompile(`APP_KEY\s*=\s*base64:AAAA`),

	// Empty catch blocks
	regexp.MustCompile(`catch\s*\([^)]+\)\s*\{\s*\}`),
	regexp.MustCompile(`except\s+\w+\s*:\s*pass\s*$`),
}

// deploymentPatterns are prohibited in application source only. A loopback
// address is a defect in code that runs against a configured host, but it is
// the correct value in a reverse-proxy upstream, a health probe, or a local
// service binding — so these are checked only under the paths in
// applicationSourceDirs.
var deploymentPatterns = []*regexp.Regexp{
	regexp.MustCompile(`http://localhost(?::\d+)?/`),
	regexp.MustCompile(`127\.0\.0\.1`),
}

// applicationSourceDirs are the trees where a hard-coded host is a defect.
// Infrastructure config, the launcher's installer, tooling and docs all name
// loopback addresses legitimately.
var applicationSourceDirs = []string{
	filepath.Join("apps", "emulator", "src", "main"),
	filepath.Join("apps", "client", "src"),
	filepath.Join("apps", "cms", "app"),
	filepath.Join("apps", "cms", "routes"),
}

// allowMarker lets a line opt out of a specific finding when the pattern is
// genuinely correct there. The reason is required so the exemption is
// reviewable rather than a silent mute.
var allowMarker = regexp.MustCompile(`placeholder-scan:allow\s+\S+`)

// skippedExtensions lists file extensions whose content is not source code.
var skippedExtensions = map[string]bool{
	".zip": true, ".jar": true, ".swf": true, ".png": true, ".jpg": true,
	".svg": true, ".ico": true, ".woff": true, ".woff2": true, ".ttf": true,
	".pdf": true, ".sql.gz": true, ".gz": true,
}

// skippedDirs lists directory names to skip entirely.
var skippedDirs = map[string]bool{
	".git": true, "node_modules": true, "vendor": true, "target": true,
	"build": true, "dist": true, ".gradle": true,
}

// skippedFiles are files whose whole purpose is to name these patterns. The
// scanners themselves define the prohibited list, so scanning their source
// reports every pattern as a finding against itself.
var skippedFiles = map[string]bool{
	"main.go": false, // resolved by path below, not by bare name
}

// scannerSources are the tools that define or document the pattern list.
var scannerSources = []string{
	filepath.Join("tools", "placeholder-scan"),
	filepath.Join("tools", "asset-validator"),
}

func main() {
	root := "."
	if len(os.Args) >= 2 {
		root = os.Args[1]
	}

	var findings []Finding
	err := filepath.Walk(root, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return nil
		}
		if info.IsDir() {
			if skippedDirs[info.Name()] {
				return filepath.SkipDir
			}
			return nil
		}
		if skippedExtensions[strings.ToLower(filepath.Ext(path))] {
			return nil
		}
		if isScannerSource(path) {
			return nil
		}
		findings = append(findings, scanFile(path)...)
		return nil
	})
	if err != nil {
		fmt.Fprintf(os.Stderr, "walk error: %v\n", err)
		os.Exit(1)
	}

	for _, f := range findings {
		fmt.Printf("%s:%d [%s] %s\n", f.File, f.Line, f.Pattern, strings.TrimSpace(f.Text))
	}

	if len(findings) > 0 {
		fmt.Fprintf(os.Stderr, "\n%d prohibited pattern(s) found. Build blocked.\n", len(findings))
		os.Exit(1)
	}
	fmt.Println("Placeholder scan clean — 0 findings.")
}

func scanFile(path string) []Finding {
	f, err := os.Open(path)
	if err != nil {
		return nil
	}
	defer f.Close()

	patterns := placeholderPatterns
	if isApplicationSource(path) {
		patterns = append(append([]*regexp.Regexp{}, placeholderPatterns...), deploymentPatterns...)
	}

	var findings []Finding
	scanner := bufio.NewScanner(f)
	line := 0
	for scanner.Scan() {
		line++
		text := scanner.Text()
		if allowMarker.MatchString(text) {
			continue
		}
		for _, pat := range patterns {
			if pat.MatchString(text) {
				findings = append(findings, Finding{
					File:    path,
					Line:    line,
					Pattern: pat.String(),
					Text:    text,
				})
				break
			}
		}
	}
	return findings
}

// isApplicationSource reports whether a hard-coded host is a defect in this
// file, rather than the correct value for a config or deployment concern.
func isApplicationSource(path string) bool {
	clean := filepath.Clean(path)
	for _, dir := range applicationSourceDirs {
		if strings.Contains(clean, dir) {
			return true
		}
	}
	return false
}

// isScannerSource reports whether a file belongs to a tool that defines the
// prohibited pattern list and would otherwise match itself.
func isScannerSource(path string) bool {
	clean := filepath.Clean(path)
	for _, dir := range scannerSources {
		if strings.Contains(clean, dir) {
			return true
		}
	}
	return false
}
