//go:build linux

package service

import (
	"os"
	"os/exec"
	"strconv"
	"strings"
)

func names() []string {
	return []string{"habnut-emulator", "habnut-horizon", "nginx", "php8.3-fpm", "mariadb", "redis-server"}
}

func systemctl(args ...string) error {
	cmd := exec.Command("systemctl", args...)
	cmd.Stdout, cmd.Stderr = os.Stdout, os.Stderr
	return cmd.Run()
}

func start(name string) error   { return systemctl("start", name) }
func stop(name string) error    { return systemctl("stop", name) }
func restart(name string) error { return systemctl("restart", name) }

func query(name string) Status {
	out, _ := exec.Command("systemctl", "is-active", name).Output()
	state := strings.TrimSpace(string(out))
	if state == "" {
		state = "unknown"
	}
	return Status{Name: name, State: state, Active: state == "active"}
}

func logs(name string, lines int, follow bool) error {
	args := []string{"-u", name, "-n", strconv.Itoa(lines), "--no-pager"}
	if follow {
		args = append(args, "-f")
	}
	cmd := exec.Command("journalctl", args...)
	cmd.Stdout, cmd.Stderr = os.Stdout, os.Stderr
	return cmd.Run()
}
