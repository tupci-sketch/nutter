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

// prohibitedPatterns lists all patterns that must not appear in committed code.
// These patterns represent placeholder markers, stub implementations, and
// hard-coded values that violate the zero-placeholder requirement.
var prohibitedPatterns = []*regexp.Regexp{
	// Stub/placeholder code markers
	regexp.MustCompile(`(?i)\bTODO\b`),
	regexp.MustCompile(`(?i)\bFIXME\b`),
	regexp.MustCompile(`(?i)\bHACK\b`),
	regexp.MustCompile(`(?i)\bNOTIMPLEMENTED\b`),
	regexp.MustCompile(`(?i)throw new NotImplementedException`),
	regexp.MustCompile(`(?i)throw new UnsupportedOperationException\(\)`),
	regexp.MustCompile(`(?i)return null; // TODO`),
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

	// Hard-coded localhost in production paths
	regexp.MustCompile(`http://localhost(?::\d+)?/`),
	regexp.MustCompile(`127\.0\.0\.1`),
}

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

// whitelistPatterns are file patterns where 127.0.0.1/localhost are expected
// (test configs, template files, docker-compose).
var whitelistFiles = map[string]bool{
	"docker-compose.yml":      true,
	"docker-compose.yaml":     true,
	"prometheus.yml":          true,
	"alertmanager.yml":        true,
	"loki.yml":                true,
	"promtail.yml":            true,
	"emulator.properties.tmpl": true,
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
		if whitelistFiles[filepath.Base(path)] {
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

	var findings []Finding
	scanner := bufio.NewScanner(f)
	line := 0
	for scanner.Scan() {
		line++
		text := scanner.Text()
		for _, pat := range prohibitedPatterns {
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
