package payload

import (
	"os"
	"path/filepath"
	"testing"
)

// TestSafeJoin covers the traversal guard applied to every archive entry.
func TestSafeJoin(t *testing.T) {
	root := t.TempDir()

	ok := []string{"a.txt", "dir/b.txt", "./dir/../c.txt"}
	for _, name := range ok {
		if _, err := safeJoin(root, name); err != nil {
			t.Errorf("safeJoin(%q) rejected a legitimate entry: %v", name, err)
		}
	}

	bad := []string{"../escape.txt", "dir/../../escape.txt", "/etc/passwd"}
	for _, name := range bad {
		if _, err := safeJoin(root, name); err == nil {
			t.Errorf("safeJoin(%q) accepted an entry that escapes the destination", name)
		}
	}
}

// TestExtractionUnbundled verifies a development build fails with a clear
// error rather than producing an empty install.
func TestExtractionUnbundled(t *testing.T) {
	if Available() {
		t.Skip("binary was built with -tags bundled")
	}
	if err := ExtractCMS(t.TempDir()); err == nil {
		t.Fatal("expected ErrNotBundled from an unbundled build, got nil")
	}
}

// TestExtractionBundled unpacks every embedded component and checks that the
// files an install depends on are actually present.
func TestExtractionBundled(t *testing.T) {
	if !Available() {
		t.Skip("binary was built without -tags bundled")
	}

	dir := t.TempDir()

	jar := filepath.Join(dir, "habnut-emulator.jar")
	if err := Emulator(jar); err != nil {
		t.Fatalf("extract emulator: %v", err)
	}
	if fi, err := os.Stat(jar); err != nil || fi.Size() < 1<<20 {
		t.Fatalf("emulator JAR missing or implausibly small: %v", err)
	}

	clientDir := filepath.Join(dir, "client")
	if err := ExtractClient(clientDir); err != nil {
		t.Fatalf("extract client: %v", err)
	}
	if _, err := os.Stat(filepath.Join(clientDir, "index.html")); err != nil {
		t.Errorf("client bundle has no index.html: %v", err)
	}

	cmsDir := filepath.Join(dir, "cms")
	if err := ExtractCMS(cmsDir); err != nil {
		t.Fatalf("extract CMS: %v", err)
	}
	for _, want := range []string{
		"artisan",
		filepath.Join("vendor", "autoload.php"),
		filepath.Join("routes", "web.php"),
		filepath.Join("app", "Http", "Controllers", "Dcc", "DccDashboardController.php"),
		filepath.Join("storage", "framework", "cache"),
	} {
		if _, err := os.Stat(filepath.Join(cmsDir, want)); err != nil {
			t.Errorf("CMS tree is missing %s: %v", want, err)
		}
	}
}
