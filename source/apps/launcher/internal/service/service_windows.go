//go:build windows

package service

import (
	"bufio"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

func names() []string {
	return []string{"HabnutEmulator", "HabnutHorizon", "nginx", "MariaDB", "Memurai"}
}

func sc(args ...string) error {
	cmd := exec.Command("sc", args...)
	cmd.Stdout, cmd.Stderr = os.Stdout, os.Stderr
	return cmd.Run()
}

func start(name string) error { return sc("start", name) }
func stop(name string) error  { return sc("stop", name) }

func restart(name string) error {
	// The Service Control Manager has no restart verb; stopping a service that
	// is already stopped is not an error worth failing the restart over.
	_ = sc("stop", name)
	return sc("start", name)
}

func query(name string) Status {
	out, err := exec.Command("sc", "query", name).Output()
	if err != nil {
		return Status{Name: name, State: "not installed", Active: false}
	}
	state := "unknown"
	for _, line := range strings.Split(string(out), "\n") {
		if !strings.Contains(line, "STATE") {
			continue
		}
		// A STATE line reads: "        STATE              : 4  RUNNING"
		fields := strings.Fields(line)
		if len(fields) > 0 {
			state = strings.ToLower(fields[len(fields)-1])
		}
		break
	}
	return Status{Name: name, State: state, Active: state == "running"}
}

// logs prints the tail of a service's log file.  Windows services write to
// files rather than a journal, so the emulator and queue worker logs are read
// from the install tree and the rest report where to look.
func logs(name string, lines int, follow bool) error {
	if follow {
		return fmt.Errorf("log following is not supported on Windows; " +
			"open the log file in a viewer that tails it instead")
	}

	root := os.Getenv("ProgramData")
	if root == "" {
		root = `C:\ProgramData`
	}
	logDir := filepath.Join(root, "Habnut", "app", "logs")

	var file string
	switch name {
	case "HabnutEmulator":
		file = filepath.Join(logDir, "emulator.log")
	case "HabnutHorizon":
		file = filepath.Join(root, "Habnut", "app", "cms", "storage", "logs", "laravel.log")
	default:
		return fmt.Errorf("no log file is tracked for %s; check the Windows Event Viewer", name)
	}

	return tail(file, lines)
}

func tail(path string, n int) error {
	f, err := os.Open(path)
	if err != nil {
		return fmt.Errorf("open %s: %w", path, err)
	}
	defer f.Close()

	ring := make([]string, 0, n)
	sc := bufio.NewScanner(f)
	sc.Buffer(make([]byte, 0, 64*1024), 1024*1024)
	for sc.Scan() {
		if len(ring) == n {
			ring = ring[1:]
		}
		ring = append(ring, sc.Text())
	}
	if err := sc.Err(); err != nil {
		return err
	}
	for _, line := range ring {
		fmt.Println(line)
	}
	return nil
}
