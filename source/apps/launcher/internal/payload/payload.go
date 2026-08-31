// Package payload provides access to the server components that ship inside
// the habnutctl binary.
//
// A release binary is built with the "bundled" build tag and embeds the
// emulator JAR, the compiled client bundle, and the CMS application tree.  That
// binary can install a complete hotel on a machine with no access to this
// source tree.  A binary built without the tag carries no components and
// reports Available() == false, which the installer surfaces as a clear error
// rather than a confusing mid-install failure.
package payload

import (
	"archive/tar"
	"compress/gzip"
	"errors"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// ErrNotBundled is returned when a component is requested from a binary that
// was built without the "bundled" tag.
var ErrNotBundled = errors.New(
	"this habnutctl binary was built without embedded server components; " +
		"use a release binary (built with -tags bundled) to install from the executable alone")

// Component names for the embedded archives.
const (
	ComponentEmulator = "emulator" // habnut-emulator.jar
	ComponentClient   = "client"   // built client bundle (tar.gz)
	ComponentCMS      = "cms"      // CMS application tree (tar.gz)
)

// Available reports whether this binary carries embedded components.
func Available() bool { return bundled }

// Version returns the build version stamped into the payload, or "unbundled".
func Version() string {
	if !bundled {
		return "unbundled"
	}
	return strings.TrimSpace(payloadVersion)
}

// Emulator writes the emulator JAR to dest.
func Emulator(dest string) error {
	data, err := component(ComponentEmulator)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(filepath.Dir(dest), 0o755); err != nil {
		return err
	}
	return os.WriteFile(dest, data, 0o644)
}

// ExtractClient unpacks the built client bundle into destDir.
func ExtractClient(destDir string) error { return extractTarGz(ComponentClient, destDir) }

// ExtractCMS unpacks the CMS application tree into destDir.
func ExtractCMS(destDir string) error { return extractTarGz(ComponentCMS, destDir) }

func extractTarGz(name, destDir string) error {
	data, err := component(name)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(destDir, 0o755); err != nil {
		return err
	}

	zr, err := gzip.NewReader(strings.NewReader(string(data)))
	if err != nil {
		return fmt.Errorf("%s: open archive: %w", name, err)
	}
	defer zr.Close()

	tr := tar.NewReader(zr)
	for {
		hdr, err := tr.Next()
		if errors.Is(err, io.EOF) {
			return nil
		}
		if err != nil {
			return fmt.Errorf("%s: read archive: %w", name, err)
		}

		target, err := safeJoin(destDir, hdr.Name)
		if err != nil {
			return fmt.Errorf("%s: %w", name, err)
		}

		switch hdr.Typeflag {
		case tar.TypeDir:
			if err := os.MkdirAll(target, os.FileMode(hdr.Mode)&os.ModePerm); err != nil {
				return err
			}
		case tar.TypeReg:
			if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
				return err
			}
			if err := writeFile(target, tr, os.FileMode(hdr.Mode)&os.ModePerm); err != nil {
				return err
			}
		case tar.TypeSymlink:
			// Symlinks are not used by any shipped component and are skipped so
			// an archive can never plant a link outside destDir.
			continue
		}
	}
}

func writeFile(target string, r io.Reader, mode os.FileMode) error {
	if mode == 0 {
		mode = 0o644
	}
	f, err := os.OpenFile(target, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, mode)
	if err != nil {
		return err
	}
	defer f.Close()
	_, err = io.Copy(f, r)
	return err
}

// safeJoin resolves name against root and rejects any path that would escape
// it, guarding against archive traversal entries such as "../../etc/passwd".
func safeJoin(root, name string) (string, error) {
	cleaned := filepath.Clean(filepath.FromSlash(name))
	if filepath.IsAbs(cleaned) || strings.HasPrefix(cleaned, "..") {
		return "", fmt.Errorf("archive entry escapes destination: %s", name)
	}
	target := filepath.Join(root, cleaned)
	rel, err := filepath.Rel(root, target)
	if err != nil || rel == ".." || strings.HasPrefix(rel, ".."+string(os.PathSeparator)) {
		return "", fmt.Errorf("archive entry escapes destination: %s", name)
	}
	return target, nil
}
