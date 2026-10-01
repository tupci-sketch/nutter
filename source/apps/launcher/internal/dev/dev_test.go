package dev

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// A hotel on somebody's own machine has one job: come up, with something in
// it, without asking for anything. These tests cover the parts of that which
// can be checked without Docker — where things are written, what goes in them,
// and that nothing is left listening to the network.

func TestDefaultRootUsesHabnutDevHomeWhenSet(t *testing.T) {
	t.Setenv("HABNUT_DEV_HOME", "/somewhere/else")

	root, err := DefaultRoot()
	if err != nil {
		t.Fatalf("DefaultRoot: %v", err)
	}
	if root != "/somewhere/else" {
		t.Errorf("DefaultRoot() = %q, want the directory that was asked for", root)
	}
}

func TestDefaultRootIsInsideTheUsersOwnSpace(t *testing.T) {
	t.Setenv("HABNUT_DEV_HOME", "")

	root, err := DefaultRoot()
	if err != nil {
		t.Fatalf("DefaultRoot: %v", err)
	}

	home, err := os.UserHomeDir()
	if err != nil {
		t.Skip("no home directory on this machine")
	}

	if !strings.HasPrefix(root, home) {
		t.Errorf("DefaultRoot() = %q, which is outside %q; a local hotel must not need "+
			"privileges or litter the machine", root, home)
	}
}

func TestEveryPublishedPortIsBoundToLoopback(t *testing.T) {
	env := New(t.TempDir(), "test")
	if err := env.Create(); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if err := env.WriteStack("base64:test", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	body, err := os.ReadFile(env.ComposeFile())
	if err != nil {
		t.Fatalf("reading the compose file: %v", err)
	}

	for _, line := range strings.Split(string(body), "\n") {
		trimmed := strings.TrimSpace(line)
		if !strings.HasPrefix(trimmed, `- "`) || !strings.Contains(trimmed, ":") {
			continue
		}
		// A published port that is not pinned to 127.0.0.1 is reachable from
		// the network, which is not what somebody starting a hotel to have a
		// look at is agreeing to.
		if !strings.Contains(trimmed, "127.0.0.1:") {
			t.Errorf("published port is not bound to loopback: %s", trimmed)
		}
	}
}

func TestStackIsWrittenWhereTheHotelExpectsIt(t *testing.T) {
	env := New(t.TempDir(), "test")
	if err := env.Create(); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if err := env.WriteStack("base64:test", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	for _, path := range []string{
		env.ComposeFile(),
		env.NginxConf(),
		filepath.Join(env.CMSDir(), ".env"),
		filepath.Join(env.CMSDir(), "dev-entrypoint.sh"),
	} {
		if _, err := os.Stat(path); err != nil {
			t.Errorf("%s was not written: %v", path, err)
		}
	}
}

func TestWriteStackLeavesYourChangesAlone(t *testing.T) {
	env := New(t.TempDir(), "test")
	if err := env.Create(); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if err := env.WriteStack("base64:test", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	// Somebody adds a service of their own to the compose file.
	mine := "# mine\nservices:\n  extra:\n    image: alpine\n"
	if err := os.WriteFile(env.ComposeFile(), []byte(mine), 0o644); err != nil {
		t.Fatalf("writing: %v", err)
	}

	if err := env.WriteStack("base64:test", false); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	body, _ := os.ReadFile(env.ComposeFile())
	if string(body) != mine {
		t.Error("a second `dev up` overwrote the compose file; it should only do that with --recreate")
	}

	// …and --recreate puts it back.
	if err := env.WriteStack("base64:test", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}
	body, _ = os.ReadFile(env.ComposeFile())
	if string(body) == mine {
		t.Error("--recreate did not rewrite the compose file")
	}
}

func TestWebsiteIsToldWhereEverythingIs(t *testing.T) {
	env := New(t.TempDir(), "test")
	env.HTTPPort = 9999
	if err := env.Create(); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if err := env.WriteStack("base64:a-key", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	body, err := os.ReadFile(filepath.Join(env.CMSDir(), ".env"))
	if err != nil {
		t.Fatalf("reading the website settings: %v", err)
	}
	settings := string(body)

	for _, want := range []string{
		"APP_KEY=base64:a-key",          // the key it was given
		"APP_URL=http://127.0.0.1:9999", // the port it was given
		"DB_HOST=db",                    // inside the stack, not on the host
		"REDIS_HOST=redis",              // the ticket has to reach the hotel
		"MAIL_HOST=mail",                // nothing leaves the machine
		"CLIENT_URL=/client/",           // same origin as the site
	} {
		if !strings.Contains(settings, want) {
			t.Errorf("the website settings are missing %q", want)
		}
	}

	if strings.Contains(settings, "APP_DEBUG=false") {
		t.Error("a local hotel should run with debugging on; seeing the error is the point")
	}
}

func TestTheWebServerServesTheSiteAndTheGameSeparately(t *testing.T) {
	env := New(t.TempDir(), "test")
	if err := env.Create(); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if err := env.WriteStack("base64:test", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	body, err := os.ReadFile(env.NginxConf())
	if err != nil {
		t.Fatalf("reading the web server config: %v", err)
	}
	conf := string(body)

	for _, want := range []string{
		"location /client/",   // the game
		"location /ws",        // its socket
		"location /",          // the website
		"fastcgi_pass cms",    // which is PHP
		"/var/www/cms/public", // served from the site's front controller
	} {
		if !strings.Contains(conf, want) {
			t.Errorf("the web server config is missing %q", want)
		}
	}

	// A local hotel has no certificate, so anything that assumes one would
	// make it unreachable.
	for _, unwanted := range []string{"ssl_certificate", "listen 443", "return 301 https"} {
		if strings.Contains(conf, unwanted) {
			t.Errorf("the local web server config contains %q, which needs a certificate", unwanted)
		}
	}
}

func TestTwoHotelsOnOneMachineDoNotCollide(t *testing.T) {
	a := New(t.TempDir(), "first")
	b := New(t.TempDir(), "second")

	if a.Project() == b.Project() {
		t.Error("two hotels share a Compose project name, so each would adopt the other's containers")
	}
}

func TestADefaultHotelComesUpWithPeopleInIt(t *testing.T) {
	env := New(t.TempDir(), "test")
	if !env.Demo {
		t.Error("a hotel with nobody in it shows nothing about whether it works, so demo content is the default")
	}
}

func TestTheHotelIsReachableAtThePortItWasGiven(t *testing.T) {
	env := New(t.TempDir(), "test")
	env.HTTPPort = 1234

	if got, want := env.URL(), "http://127.0.0.1:1234"; got != want {
		t.Errorf("URL() = %q, want %q", got, want)
	}
}

func TestTheWebsitesImageCarriesWhatTheWebsiteNeeds(t *testing.T) {
	env := New(t.TempDir(), "test")
	if err := env.Create(); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if err := env.WriteStack("base64:test", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	body, err := os.ReadFile(env.CMSDockerfile())
	if err != nil {
		t.Fatalf("the website has no Dockerfile: %v", err)
	}
	dockerfile := string(body)

	// pdo_mysql is how the website reaches the database and is not in the
	// official images; bcmath and pcntl are declared requirements of packages
	// it ships with. Compiling any of them needs the build tools those images
	// drop, so they are installed and removed in the same layer.
	for _, want := range []string{
		"pdo_mysql", "bcmath", "pcntl",
		"$PHPIZE_DEPS", // without this the extensions cannot be compiled
		"apk del .build-deps",
	} {
		if !strings.Contains(dockerfile, want) {
			t.Errorf("the website's Dockerfile is missing %q", want)
		}
	}

	if !strings.Contains(dockerfile, "display_errors=On") {
		t.Error("a local hotel should show its errors; that is the point of running one")
	}
}

func TestNothingInstallsAnExtensionAtContainerStart(t *testing.T) {
	env := New(t.TempDir(), "test")
	if err := env.Create(); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if err := env.WriteStack("base64:test", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	body, err := os.ReadFile(filepath.Join(env.CMSDir(), "dev-entrypoint.sh"))
	if err != nil {
		t.Fatalf("reading the entrypoint: %v", err)
	}

	// Compiling an extension on every container start needs a compiler
	// installed every time, which is slow and fails outright with no network.
	// It belongs in the image.
	if strings.Contains(string(body), "docker-php-ext-install") {
		t.Error("the entrypoint compiles a PHP extension; that belongs in the Dockerfile")
	}
}

func TestEveryServiceEitherPullsOrBuilds(t *testing.T) {
	env := New(t.TempDir(), "test")
	if err := env.Create(); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if err := env.WriteStack("base64:test", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	body, err := os.ReadFile(env.ComposeFile())
	if err != nil {
		t.Fatalf("reading the compose file: %v", err)
	}

	// A service with neither cannot start, and compose reports it in a way
	// that does not name the service.
	var current string
	services := map[string]bool{}
	for _, line := range strings.Split(string(body), "\n") {
		if strings.HasPrefix(line, "  ") && !strings.HasPrefix(line, "   ") &&
			strings.HasSuffix(strings.TrimSpace(line), ":") &&
			!strings.HasPrefix(strings.TrimSpace(line), "#") {
			current = strings.TrimSuffix(strings.TrimSpace(line), ":")
			if current != "" {
				services[current] = false
			}
			continue
		}
		trimmed := strings.TrimSpace(line)
		if current != "" && (strings.HasPrefix(trimmed, "image:") || trimmed == "build:") {
			services[current] = true
		}
	}

	for name, ok := range services {
		if name == "db_data" {
			continue // a volume, not a service
		}
		if !ok {
			t.Errorf("service %q has neither an image to pull nor a Dockerfile to build", name)
		}
	}

	if len(services) < 5 {
		t.Errorf("only found %d services; the parser is wrong, not the file", len(services))
	}
}

func TestTheHotelSaysWhetherItIsAlive(t *testing.T) {
	env := New(t.TempDir(), "test")
	if err := env.Create(); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if err := env.WriteStack("base64:test", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	body, err := os.ReadFile(env.ComposeFile())
	if err != nil {
		t.Fatalf("reading the compose file: %v", err)
	}
	compose := string(body)

	// The website comes up perfectly well without a hotel behind it, so
	// without this a dead game server passes for a working one and `dev up`
	// reports success on a hotel nobody can enter.
	emulator := serviceBlock(compose, "emulator")
	if !strings.Contains(emulator, "healthcheck:") {
		t.Error("the hotel has no healthcheck, so a crashed one would report as running")
	}

	// curl is not in the Temurin images, so a healthcheck that used it would
	// fail on a perfectly healthy hotel. Only the command matters here — the
	// comment above it names curl to explain why it is not used.
	for _, line := range strings.Split(emulator, "\n") {
		trimmed := strings.TrimSpace(line)
		if !strings.HasPrefix(trimmed, "test:") {
			continue
		}
		if strings.Contains(trimmed, "curl") || strings.Contains(trimmed, "wget") {
			t.Errorf("the healthcheck uses a tool the Temurin images do not carry: %s", trimmed)
		}
		if !strings.Contains(trimmed, "/dev/tcp/") {
			t.Errorf("the healthcheck does not check the port the hotel listens on: %s", trimmed)
		}
		return
	}
	t.Error("the hotel's healthcheck has no command")
}

func TestTheWebsiteAsksForNoPicturesNothingIsServing(t *testing.T) {
	env := New(t.TempDir(), "test")
	if err := env.Create(); err != nil {
		t.Fatalf("Create: %v", err)
	}
	if err := env.WriteStack("base64:test", true); err != nil {
		t.Fatalf("WriteStack: %v", err)
	}

	body, err := os.ReadFile(filepath.Join(env.CMSDir(), ".env"))
	if err != nil {
		t.Fatalf("reading the website settings: %v", err)
	}

	// No imager runs in a local stack and there is no asset pack for one to
	// draw from. An address here would put a broken image on every page that
	// shows a figure; empty makes the site draw a monogram instead.
	for _, line := range strings.Split(string(body), "\n") {
		if strings.HasPrefix(line, "IMAGER_URL=") {
			if strings.TrimSpace(strings.TrimPrefix(line, "IMAGER_URL=")) != "" {
				t.Errorf("IMAGER_URL points somewhere, but nothing in the local stack "+
					"serves pictures: %q", line)
			}
			return
		}
	}
	t.Error("the website settings never mention IMAGER_URL")
}

// serviceBlock returns one service's lines out of a compose file.
func serviceBlock(compose, service string) string {
	lines := strings.Split(compose, "\n")
	var out []string
	inside := false

	for _, line := range lines {
		isServiceHeader := strings.HasPrefix(line, "  ") &&
			!strings.HasPrefix(line, "   ") &&
			strings.HasSuffix(strings.TrimSpace(line), ":") &&
			!strings.HasPrefix(strings.TrimSpace(line), "#")

		if isServiceHeader {
			inside = strings.TrimSpace(line) == service+":"
			continue
		}
		if inside {
			out = append(out, line)
		}
	}
	return strings.Join(out, "\n")
}
