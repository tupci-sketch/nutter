// Package dev runs a whole hotel on one machine, for looking at.
//
// `habnutctl install` sets a hotel up on a server: it wants root, a domain, a
// certificate, system packages and services that start at boot. None of that
// is appropriate when somebody wants to see whether the thing works before
// they commit a server to it.
//
// This is the other mode. Everything lives under one directory in the user's
// own home, nothing is installed system-wide, nothing listens anywhere but
// 127.0.0.1, and there is no domain and no TLS. It comes up seeded with a
// hotel that has people and furniture in it, prints a URL, and can be thrown
// away again with one command.
package dev

import (
	"fmt"
	"os"
	"path/filepath"
	"runtime"
)

// Default ports. All bound to loopback, and all chosen high enough to need no
// privileges — a local hotel should never ask for a password.
const (
	DefaultHTTPPort    = 8088
	DefaultDBPort      = 33061
	DefaultRedisPort   = 63791
	DefaultMailPort    = 8025
	DefaultEmulatorWS  = 3010
	DefaultEmulatorAPI = 3011
	DefaultImagerPort  = 8081
)

// Env is one local hotel: where its files are and which ports it uses.
type Env struct {
	// Root holds everything this hotel owns. Deleting it deletes the hotel.
	Root string

	// Name distinguishes one local hotel from another on the same machine,
	// and prefixes the container names so two do not collide.
	Name string

	HTTPPort   int
	DBPort     int
	RedisPort  int
	MailPort   int
	WSPort     int
	APIPort    int
	ImagerPort int

	// Demo fills the hotel with accounts, rooms and furniture. On by default:
	// an empty hotel shows nothing about whether it works.
	Demo bool

	// RP brings up the roleplay city beside the hotel.
	RP bool
}

// New describes a local hotel with the usual settings.
func New(root, name string) *Env {
	if name == "" {
		name = "habnut-dev"
	}
	return &Env{
		Root:       root,
		Name:       name,
		HTTPPort:   DefaultHTTPPort,
		DBPort:     DefaultDBPort,
		RedisPort:  DefaultRedisPort,
		MailPort:   DefaultMailPort,
		WSPort:     DefaultEmulatorWS,
		APIPort:    DefaultEmulatorAPI,
		ImagerPort: DefaultImagerPort,
		Demo:       true,
	}
}

// DefaultRoot is where a local hotel lives when nobody says otherwise.
//
// Inside the user's own data directory, so it needs no privileges, survives a
// reboot, and does not litter the machine: one directory holds the lot.
func DefaultRoot() (string, error) {
	if explicit := os.Getenv("HABNUT_DEV_HOME"); explicit != "" {
		return explicit, nil
	}

	switch runtime.GOOS {
	case "windows":
		if dir := os.Getenv("LOCALAPPDATA"); dir != "" {
			return filepath.Join(dir, "Habnut", "dev"), nil
		}
	case "darwin":
		home, err := os.UserHomeDir()
		if err != nil {
			return "", err
		}
		return filepath.Join(home, "Library", "Application Support", "Habnut", "dev"), nil
	}

	home, err := os.UserHomeDir()
	if err != nil {
		return "", fmt.Errorf("could not work out where your home directory is: %w", err)
	}
	return filepath.Join(home, ".habnut", "dev"), nil
}

// Paths inside the hotel's directory.

func (e *Env) ComposeFile() string { return filepath.Join(e.Root, "docker-compose.yml") }
func (e *Env) EnvFile() string     { return filepath.Join(e.Root, ".env") }
func (e *Env) EmulatorJar() string { return filepath.Join(e.Root, "emulator", "habnut-emulator.jar") }
func (e *Env) CMSDir() string      { return filepath.Join(e.Root, "cms") }
func (e *Env) ClientDir() string   { return filepath.Join(e.Root, "client") }
func (e *Env) AssetsDir() string   { return filepath.Join(e.Root, "assets") }
func (e *Env) NginxConf() string   { return filepath.Join(e.Root, "nginx.conf") }
func (e *Env) StateFile() string   { return filepath.Join(e.Root, "state.json") }
func (e *Env) SeedDir() string     { return filepath.Join(e.Root, "seed") }
func (e *Env) LogDir() string      { return filepath.Join(e.Root, "logs") }

// URL is where to point a browser once the hotel is up.
func (e *Env) URL() string {
	return fmt.Sprintf("http://127.0.0.1:%d", e.HTTPPort)
}

// MailURL is where mail the hotel sends can be read. Nothing leaves the
// machine: the local stack catches it all so a registration or a password
// reset can be followed through without a mail server.
func (e *Env) MailURL() string {
	return fmt.Sprintf("http://127.0.0.1:%d", e.MailPort)
}

// Project is the Docker Compose project name, which is what keeps two local
// hotels on one machine from adopting each other's containers.
func (e *Env) Project() string { return e.Name }

// Dirs are the directories a local hotel needs before anything is written.
func (e *Env) Dirs() []string {
	return []string{
		e.Root,
		filepath.Join(e.Root, "emulator"),
		e.CMSDir(),
		e.ClientDir(),
		e.AssetsDir(),
		e.SeedDir(),
		e.LogDir(),
		filepath.Join(e.Root, "db"),
	}
}

// Create makes the directories. Safe to call on a hotel that already exists.
func (e *Env) Create() error {
	for _, dir := range e.Dirs() {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return fmt.Errorf("could not create %s: %w", dir, err)
		}
	}
	return nil
}

// Exists reports whether this hotel has been set up here before.
func (e *Env) Exists() bool {
	_, err := os.Stat(e.ComposeFile())
	return err == nil
}
