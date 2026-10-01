//go:build bundled

package payload

import (
	"os"
	"path/filepath"
	"testing"
)

// What a release binary actually carries.
//
// Only compiled into a build made with the `bundled` tag, so an ordinary
// `go test ./...` skips it. A release build runs it, and it is the one check
// that the three components somebody downloads are really inside the file they
// downloaded — and that they unpack into something that looks like a hotel.

func TestAReleaseBinaryCarriesEveryComponent(t *testing.T) {
	if !Available() {
		t.Fatal("built with -tags bundled but Available() is false")
	}
	if Version() == "unbundled" || Version() == "" {
		t.Errorf("Version() = %q, which is not a version", Version())
	}
}

func TestTheEmulatorUnpacksAsARunnableJar(t *testing.T) {
	dest := filepath.Join(t.TempDir(), "habnut-emulator.jar")

	if err := Emulator(dest); err != nil {
		t.Fatalf("Emulator: %v", err)
	}

	info, err := os.Stat(dest)
	if err != nil {
		t.Fatalf("the JAR was not written: %v", err)
	}
	if info.Size() < 1<<20 {
		t.Errorf("the JAR is %d bytes, which is too small to be the hotel", info.Size())
	}

	// A JAR is a zip; its first two bytes say so. A truncated or empty embed
	// would otherwise only fail when somebody tried to start the hotel.
	f, err := os.Open(dest)
	if err != nil {
		t.Fatalf("opening the JAR: %v", err)
	}
	defer f.Close()

	magic := make([]byte, 2)
	if _, err := f.Read(magic); err != nil {
		t.Fatalf("reading the JAR: %v", err)
	}
	if magic[0] != 'P' || magic[1] != 'K' {
		t.Errorf("the JAR does not start with a zip header; the embed is damaged")
	}
}

func TestTheWebsiteUnpacksWithItsDependencies(t *testing.T) {
	dest := t.TempDir()

	if err := ExtractCMS(dest); err != nil {
		t.Fatalf("ExtractCMS: %v", err)
	}

	for _, path := range []string{
		"artisan",             // the website can be driven
		"public/index.php",    // it has a front controller to serve
		"vendor/autoload.php", // its dependencies came with it
		"config/habnut.php",   // it knows where the hotel is
		"app/Models/User.php", // and what an account is
		"database/migrations", // and how to build its own tables
	} {
		if _, err := os.Stat(filepath.Join(dest, path)); err != nil {
			t.Errorf("the website is missing %s: %v", path, err)
		}
	}
}

func TestTheGameClientUnpacksAsAServablePage(t *testing.T) {
	dest := t.TempDir()

	if err := ExtractClient(dest); err != nil {
		t.Fatalf("ExtractClient: %v", err)
	}

	index := filepath.Join(dest, "index.html")
	body, err := os.ReadFile(index)
	if err != nil {
		t.Fatalf("the client has no index.html: %v", err)
	}

	// Built with base=/client/, because that is where it is served from on
	// both a real install and a local hotel. A bundle built for / would load
	// nothing, with no error anybody could read.
	if !contains(string(body), "/client/assets/") {
		t.Error("the client bundle does not reference /client/assets/; " +
			"it was built for the wrong base path and will load nothing")
	}

	entries, err := os.ReadDir(filepath.Join(dest, "assets"))
	if err != nil || len(entries) == 0 {
		t.Errorf("the client has no assets directory: %v", err)
	}
}

func contains(haystack, needle string) bool {
	return len(haystack) >= len(needle) && indexOf(haystack, needle) >= 0
}

func indexOf(haystack, needle string) int {
	for i := 0; i+len(needle) <= len(haystack); i++ {
		if haystack[i:i+len(needle)] == needle {
			return i
		}
	}
	return -1
}
