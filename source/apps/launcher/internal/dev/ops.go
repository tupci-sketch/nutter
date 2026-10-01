package dev

import (
	"context"
	"fmt"
	"io"
	"os"
	"strings"

	"github.com/habnut/launcher/internal/seed"
)

// Down stops a local hotel. Its data stays unless purge is set.
func Down(ctx context.Context, s *Session, purge bool) error {
	if !s.Env.Exists() {
		s.logf("There is no local hotel at %s.", s.Env.Root)
		return nil
	}

	if purge {
		s.logf("Stopping the hotel and deleting its database...")
	} else {
		s.logf("Stopping the hotel. Its database is kept; `habnutctl dev up` picks up where it left off.")
	}

	return s.Docker.Down(ctx, s.Out, purge)
}

// Reset tears a local hotel down and builds it again from nothing.
//
// The point of a local hotel is being able to break it, so getting back to a
// known state has to be one command rather than a list of things to remember.
func Reset(ctx context.Context, s *Session) error {
	s.logf("Deleting this hotel and starting again.")

	if s.Env.Exists() {
		if err := s.Docker.Down(ctx, io.Discard, true); err != nil {
			return fmt.Errorf("could not stop the old hotel: %w", err)
		}
	}

	// The generated files are rewritten rather than reused: a reset that kept
	// a compose file somebody had broken would not be a reset.
	s.Recreate = true

	for _, dir := range []string{s.Env.CMSDir(), s.Env.ClientDir(), s.Env.SeedDir()} {
		if err := os.RemoveAll(dir); err != nil {
			return fmt.Errorf("could not clear %s: %w", dir, err)
		}
	}

	return Up(ctx, s)
}

// Seed applies the seed files to a running hotel.
//
// Useful on its own after editing them: the files are written into the hotel's
// directory, so they can be changed and reapplied without rebuilding anything.
func Seed(ctx context.Context, s *Session, includeDemo bool) error {
	s.logf("Adding rooms, furniture and a catalogue...")
	if err := s.applySQL(ctx, "base.sql", readSeed(s, "base.sql", seed.Base())); err != nil {
		return err
	}

	if includeDemo {
		s.logf("Filling the hotel with people...")
		if err := s.applySQL(ctx, "demo.sql", readSeed(s, "demo.sql", seed.Demo())); err != nil {
			return err
		}
	}

	s.logf("Done. Nothing that was already there has been changed.")
	return nil
}

// readSeed prefers the copy in the hotel's directory, so an edited seed is the
// one that gets applied. Falls back to the one inside the binary.
func readSeed(s *Session, name, builtin string) string {
	body, err := os.ReadFile(s.Env.SeedDir() + "/" + name)
	if err != nil {
		return builtin
	}
	return string(body)
}

// Status describes a local hotel: whether it is up, and where to find it.
func Status(ctx context.Context, s *Session) error {
	if !s.Env.Exists() {
		s.logf("No local hotel at %s.", s.Env.Root)
		s.logf("")
		s.logf("Start one with:  habnutctl dev up")
		return nil
	}

	statuses, err := s.Docker.Status(ctx)
	if err != nil {
		return fmt.Errorf("could not ask Docker what is running: %w", err)
	}

	if len(statuses) == 0 {
		s.logf("The hotel at %s is set up but not running.", s.Env.Root)
		s.logf("")
		s.logf("Start it with:  habnutctl dev up")
		return nil
	}

	s.logf("Hotel at %s", s.Env.Root)
	s.logf("")
	s.logf("  %-12s %-10s %s", "SERVICE", "STATE", "HEALTH")
	for _, st := range statuses {
		health := st.Health
		if health == "" {
			health = "—"
		}
		s.logf("  %-12s %-10s %s", st.Service, st.State, health)
	}

	running := 0
	for _, st := range statuses {
		if strings.EqualFold(st.State, "running") {
			running++
		}
	}

	if running == len(statuses) {
		s.logf("")
		PrintWhereToGo(s.Out, s.Env)
	}

	return nil
}

// PrintWhereToGo says what to do next, which is the whole point of a local
// hotel: a URL, an account, and where the mail goes.
func PrintWhereToGo(out io.Writer, env *Env) {
	fmt.Fprintf(out, "  The hotel      %s\n", env.URL())
	fmt.Fprintf(out, "  The game       %s/hotel\n", env.URL())
	fmt.Fprintf(out, "  Staff pages    %s/dcc\n", env.URL())
	fmt.Fprintf(out, "  Mail it sends  %s\n", env.MailURL())
	fmt.Fprintln(out)

	if !env.Demo {
		fmt.Fprintln(out, "  Started with --no-demo, so there are no accounts yet.")
		fmt.Fprintln(out, "  Make one at", env.URL()+"/register")
		return
	}

	fmt.Fprintln(out, "  Sign in with any of these. The password is:", seed.DemoPassword)
	for _, acc := range seed.DemoAccounts {
		fmt.Fprintf(out, "    %-8s %-14s %s\n", acc.Username, acc.Rank, acc.Note)
	}
}

// PrintAssetNote explains what is and is not there without an asset pack.
//
// Worth saying plainly: somebody who opens the game and sees coloured shapes
// should know the hotel is working and the pictures are missing, rather than
// concluding the hotel is broken.
func PrintAssetNote(out io.Writer, env *Env) {
	if hasAssets(env) {
		return
	}

	fmt.Fprintln(out)
	fmt.Fprintln(out, "  No asset pack is installed, so rooms and figures draw as plain shapes.")
	fmt.Fprintln(out, "  Everything else works: walking, chat, furniture, the catalogue, the site.")
	fmt.Fprintln(out, "  To add one:  habnutctl swf install <pack.zip>")
}

func hasAssets(env *Env) bool {
	entries, err := os.ReadDir(env.AssetsDir())
	return err == nil && len(entries) > 0
}
