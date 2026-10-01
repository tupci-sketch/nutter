package dev

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/habnut/launcher/internal/payload"
	"github.com/habnut/launcher/internal/seed"
)

// Step is one thing that has to happen for a local hotel to come up.
type Step struct {
	Name string
	Run  func(ctx context.Context, s *Session) error
}

// Session carries what the steps need: the hotel, Docker, and somewhere to
// report to.
type Session struct {
	Env    *Env
	Docker *Docker
	Out    io.Writer

	// Recreate rewrites the generated files even when they already exist,
	// discarding any changes made to them.
	Recreate bool
}

func (s *Session) logf(format string, args ...any) {
	fmt.Fprintf(s.Out, format+"\n", args...)
}

// Steps is everything `habnutctl dev up` does, in order.
func Steps() []Step {
	return []Step{
		{"Create the hotel's directory", stepCreateDirs},
		{"Write the stack", stepWriteStack},
		{"Unpack the hotel", stepUnpackPayload},
		{"Fetch container images", stepPullImages},
		{"Start the database and cache", stepStartData},
		{"Apply the hotel's schema", stepMigrate},
		{"Add rooms, furniture and a catalogue", stepSeedBase},
		{"Fill the hotel with people", stepSeedDemo},
		{"Start the hotel and the website", stepStartApps},
		{"Wait for the hotel to answer", stepWaitReady},
	}
}

// Up runs every step, reporting progress as it goes.
func Up(ctx context.Context, s *Session) error {
	steps := Steps()
	for i, step := range steps {
		s.logf("[%d/%d] %s", i+1, len(steps), step.Name)
		if err := step.Run(ctx, s); err != nil {
			return fmt.Errorf("%s: %w", strings.ToLower(step.Name), err)
		}
	}
	return nil
}

func stepCreateDirs(_ context.Context, s *Session) error {
	return s.Env.Create()
}

func stepWriteStack(_ context.Context, s *Session) error {
	key, err := AppKey(s.Env)
	if err != nil {
		return err
	}
	return s.Env.WriteStack(key, s.Recreate)
}

// AppKey returns this hotel's application key, making one the first time.
//
// Kept in a file rather than regenerated each run: a new key would invalidate
// every signed cookie, so everybody would be silently logged out each time the
// hotel was restarted.
func AppKey(env *Env) (string, error) {
	path := filepath.Join(env.Root, "app.key")

	if existing, err := os.ReadFile(path); err == nil {
		if key := strings.TrimSpace(string(existing)); key != "" {
			return key, nil
		}
	}

	raw := make([]byte, 32)
	if _, err := rand.Read(raw); err != nil {
		return "", fmt.Errorf("could not generate an application key: %w", err)
	}
	key := "base64:" + base64.StdEncoding.EncodeToString(raw)

	if err := os.WriteFile(path, []byte(key+"\n"), 0o600); err != nil {
		return "", fmt.Errorf("could not save the application key: %w", err)
	}
	return key, nil
}

// stepUnpackPayload writes the hotel, the website and the game client out of
// this binary into the hotel's directory.
func stepUnpackPayload(_ context.Context, s *Session) error {
	if !payload.Available() {
		return fmt.Errorf("%w\n\nA local hotel needs the server components, which only a "+
			"release build carries. Build one with:\n  make release", payload.ErrNotBundled)
	}

	if err := payload.Emulator(s.Env.EmulatorJar()); err != nil {
		return fmt.Errorf("could not unpack the hotel: %w", err)
	}

	// The website's settings and entrypoint were written before this, so the
	// unpack must not clear the directory out from under them.
	if err := payload.ExtractCMS(s.Env.CMSDir()); err != nil {
		return fmt.Errorf("could not unpack the website: %w", err)
	}
	if err := payload.ExtractClient(s.Env.ClientDir()); err != nil {
		return fmt.Errorf("could not unpack the game client: %w", err)
	}

	// The seed files go in beside them so they can be read, edited and
	// reapplied with `habnutctl dev seed`.
	for name, body := range map[string]string{
		"base.sql": seed.Base(),
		"demo.sql": seed.Demo(),
	} {
		path := filepath.Join(s.Env.SeedDir(), name)
		if err := os.WriteFile(path, []byte(body), 0o644); err != nil {
			return fmt.Errorf("could not write %s: %w", path, err)
		}
	}

	return s.ensureWritableStorage()
}

// ensureWritableStorage makes the directories Laravel writes to.
//
// They are empty in the shipped tree, so an archive does not carry them, and
// a website that cannot write its own cache fails on the first request with an
// error that says nothing about directories.
func (s *Session) ensureWritableStorage() error {
	for _, dir := range []string{
		"storage/app", "storage/framework/cache/data", "storage/framework/sessions",
		"storage/framework/testing", "storage/framework/views", "storage/logs",
		"bootstrap/cache",
	} {
		full := filepath.Join(s.Env.CMSDir(), dir)
		if err := os.MkdirAll(full, 0o777); err != nil {
			return fmt.Errorf("could not create %s: %w", full, err)
		}
		// The website runs as another user inside its container, so these have
		// to be writable by anybody. They hold a local hotel's cache.
		if err := os.Chmod(full, 0o777); err != nil {
			return fmt.Errorf("could not make %s writable: %w", full, err)
		}
	}
	return nil
}

func stepPullImages(ctx context.Context, s *Session) error {
	// A slow or missing network should not stop a hotel whose images are
	// already here, so this is allowed to fail.
	if err := s.Docker.Pull(ctx, io.Discard); err != nil {
		s.logf("      (could not fetch images; using whatever is already here)")
	}
	return nil
}

// stepStartData brings up only what the schema needs, so migrations can run
// before the hotel tries to read a table that is not there yet.
func stepStartData(ctx context.Context, s *Session) error {
	name, args := s.Docker.composeArgs("up", "-d", "db", "redis", "mail")
	if err := run(ctx, io.Discard, name, args...); err != nil {
		return fmt.Errorf("could not start the database: %w", err)
	}

	s.logf("      waiting for the database (first run takes a minute)")
	return s.Docker.WaitHealthy(ctx, "db", 3*time.Minute)
}

// stepMigrate applies the hotel's schema.
//
// Run through the emulator's own migrator rather than a SQL file, so a local
// hotel is built by exactly the thing that builds a real one.
func stepMigrate(ctx context.Context, s *Session) error {
	name, args := s.Docker.composeArgs("run", "--rm", "--no-deps",
		"-e", "MIGRATE_ONLY=true", "emulator",
		"java", "-cp", "/app/habnut-emulator.jar",
		"com.habnut.emulator.db.MigrationRunner")

	var out bytes.Buffer
	if err := run(ctx, &out, name, args...); err != nil {
		return fmt.Errorf("the schema could not be applied: %w\n%s", err, out.String())
	}
	return nil
}

func stepSeedBase(ctx context.Context, s *Session) error {
	return s.applySQL(ctx, "base.sql", seed.Base())
}

func stepSeedDemo(ctx context.Context, s *Session) error {
	if !s.Env.Demo {
		s.logf("      skipped (--no-demo)")
		return nil
	}
	return s.applySQL(ctx, "demo.sql", seed.Demo())
}

// applySQL feeds a seed file to the database through the database's own
// container, so nothing has to be installed on the host to run it.
func (s *Session) applySQL(ctx context.Context, name, sql string) error {
	var out bytes.Buffer
	err := s.Docker.ExecInput(ctx, &out, strings.NewReader(sql),
		"db", "mariadb", "-uhabnut", "-phabnut", "habnut")
	if err != nil {
		return fmt.Errorf("%s could not be applied: %w\n%s", name, err, out.String())
	}
	return nil
}

func stepStartApps(ctx context.Context, s *Session) error {
	return s.Docker.Up(ctx, io.Discard)
}

// stepWaitReady waits for the website to answer, which is the first moment the
// URL this prints is worth opening.
func stepWaitReady(ctx context.Context, s *Session) error {
	s.logf("      waiting for the website to come up")
	if err := s.Docker.WaitHealthy(ctx, "web", 2*time.Minute); err != nil {
		return err
	}
	return waitForHTTP(ctx, s.Env.URL(), 2*time.Minute)
}
