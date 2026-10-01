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
