package dev

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"runtime"
	"strings"
	"time"
)

// ErrNoDocker is returned when Docker is not installed or not running.
//
// It is the one thing a local hotel needs that cannot be shipped inside a
// binary: a database, a cache, PHP and a JVM are four separate installs
// otherwise, and getting them agreeing with each other is exactly the work
// this is meant to save.
var ErrNoDocker = errors.New("docker is not available")

// DockerAdvice says how to get Docker, in the words of the platform the user
// is actually on. A message that names the right installer is the difference
// between a two-minute fix and giving up.
func DockerAdvice() string {
	switch runtime.GOOS {
	case "windows":
		return "Install Docker Desktop from https://docs.docker.com/desktop/install/windows-install/\n" +
			"and start it, then run this again. Docker Desktop must be running, not just installed."
	case "darwin":
		return "Install Docker Desktop from https://docs.docker.com/desktop/install/mac-install/\n" +
			"and start it, then run this again. Docker Desktop must be running, not just installed."
	default:
		return "Install Docker Engine for your distribution:\n" +
			"  https://docs.docker.com/engine/install/\n" +
			"Then add yourself to the docker group so this does not need sudo:\n" +
			"  sudo usermod -aG docker $USER   (log out and back in afterwards)"
	}
}

// Docker runs compose commands for one local hotel.
type Docker struct {
	env *Env
	// compose is how this machine invokes Compose: the plugin ("docker
	// compose") on anything current, or the standalone binary on an older one.
	compose []string
}

// NewDocker checks that Docker is there and working, and works out how Compose
// is invoked on this machine.
func NewDocker(ctx context.Context, env *Env) (*Docker, error) {
	if _, err := exec.LookPath("docker"); err != nil {
		return nil, fmt.Errorf("%w: the `docker` command is not on your PATH", ErrNoDocker)
	}

	// Installed is not the same as running: on Windows and macOS the engine
	// lives in a VM that has to be started. `docker info` is what tells them
	// apart, and the difference matters because the advice differs.
	if err := run(ctx, nil, "docker", "info"); err != nil {
		return nil, fmt.Errorf("%w: docker is installed but not running", ErrNoDocker)
	}

	for _, candidate := range [][]string{{"docker", "compose"}, {"docker-compose"}} {
		args := append(append([]string{}, candidate[1:]...), "version")
		if err := run(ctx, nil, candidate[0], args...); err == nil {
			return &Docker{env: env, compose: candidate}, nil
		}
	}

	return nil, fmt.Errorf("%w: docker is running but has no compose plugin; "+
		"install it with your package manager (docker-compose-plugin) or use Docker Desktop", ErrNoDocker)
}

// composeArgs builds a compose invocation for this hotel's project and file.
func (d *Docker) composeArgs(args ...string) (string, []string) {
	full := append([]string{}, d.compose[1:]...)
	full = append(full, "-f", d.env.ComposeFile(), "-p", d.env.Project())
	full = append(full, args...)
	return d.compose[0], full
}

// Up starts the stack and waits for the containers to be created.
func (d *Docker) Up(ctx context.Context, out io.Writer) error {
	name, args := d.composeArgs("up", "-d", "--remove-orphans")
	return run(ctx, out, name, args...)
}

// Down stops the stack. With volumes, it also deletes the database, which is
// what "start again from nothing" means.
func (d *Docker) Down(ctx context.Context, out io.Writer, volumes bool) error {
	extra := []string{"down", "--remove-orphans"}
	if volumes {
		extra = append(extra, "-v")
	}
	name, args := d.composeArgs(extra...)
	return run(ctx, out, name, args...)
}

// Pull fetches the images ahead of time, so the first `up` does not sit
// silently for several minutes looking broken.
//
// --ignore-buildable skips the website, which is built rather than pulled.
func (d *Docker) Pull(ctx context.Context, out io.Writer) error {
	name, args := d.composeArgs("pull", "--quiet", "--ignore-buildable")
	if err := run(ctx, out, name, args...); err == nil {
		return nil
	}
	// Older Compose versions do not know --ignore-buildable; without it the
	// pull is still worth attempting.
	name, args = d.composeArgs("pull", "--quiet")
	return run(ctx, out, name, args...)
}

// Build builds the images this stack compiles rather than pulls.
//
// Run as its own step because on a first run it is the slow part, and a
// two-minute silence inside `up` is indistinguishable from a hang.
func (d *Docker) Build(ctx context.Context, out io.Writer, services ...string) error {
	extra := append([]string{"build"}, services...)
	name, args := d.composeArgs(extra...)
	return run(ctx, out, name, args...)
}

// Logs prints the stack's output. Following never returns on its own.
func (d *Docker) Logs(ctx context.Context, out io.Writer, service string, follow bool, tail int) error {
	extra := []string{"logs", fmt.Sprintf("--tail=%d", tail)}
	if follow {
		extra = append(extra, "--follow")
	}
	if service != "" {
		extra = append(extra, service)
	}
	name, args := d.composeArgs(extra...)
	return run(ctx, out, name, args...)
}

// ServiceStatus is one container's state, as compose reports it.
type ServiceStatus struct {
	Service string
	State   string
	Health  string
	Ports   string
}

// Status lists the stack's containers.
func (d *Docker) Status(ctx context.Context) ([]ServiceStatus, error) {
	name, args := d.composeArgs("ps", "--format", "{{.Service}}\t{{.State}}\t{{.Health}}\t{{.Ports}}")

	var out bytes.Buffer
	if err := run(ctx, &out, name, args...); err != nil {
		return nil, err
	}

	var statuses []ServiceStatus
	for _, line := range strings.Split(strings.TrimSpace(out.String()), "\n") {
		if strings.TrimSpace(line) == "" {
			continue
		}
		fields := strings.Split(line, "\t")
		s := ServiceStatus{Service: fields[0]}
		if len(fields) > 1 {
			s.State = fields[1]
		}
		if len(fields) > 2 {
			s.Health = fields[2]
		}
		if len(fields) > 3 {
			s.Ports = fields[3]
		}
		statuses = append(statuses, s)
	}
	return statuses, nil
}

// Exec runs a command inside one of the stack's containers.
func (d *Docker) Exec(ctx context.Context, out io.Writer, service string, command ...string) error {
	extra := append([]string{"exec", "-T", service}, command...)
	name, args := d.composeArgs(extra...)
	return run(ctx, out, name, args...)
}

// ExecInput runs a command inside a container, feeding it stdin. This is how
// SQL reaches the database without a client installed on the host.
func (d *Docker) ExecInput(ctx context.Context, out io.Writer, stdin io.Reader, service string, command ...string) error {
	extra := append([]string{"exec", "-T", service}, command...)
	name, args := d.composeArgs(extra...)

	cmd := exec.CommandContext(ctx, name, args...)
	cmd.Stdin = stdin
	if out != nil {
		cmd.Stdout = out
		cmd.Stderr = out
	}
	return cmd.Run()
}

// WaitHealthy waits until a service reports healthy, or the deadline passes.
//
// A hotel that is asked to migrate before its database has finished starting
// fails in a way that looks like a bug in the hotel, so this waits rather than
// letting that happen.
func (d *Docker) WaitHealthy(ctx context.Context, service string, timeout time.Duration) error {
	deadline := time.Now().Add(timeout)

	for {
		statuses, err := d.Status(ctx)
		if err == nil {
			for _, s := range statuses {
				if s.Service != service {
					continue
				}
				if strings.EqualFold(s.Health, "healthy") {
					return nil
				}
				// A service with no health check is ready once it is running.
				if s.Health == "" && strings.EqualFold(s.State, "running") {
					return nil
				}
				if strings.EqualFold(s.State, "exited") {
					return fmt.Errorf("%s stopped while starting up; run `habnutctl dev logs %s` to see why",
						service, service)
				}
			}
		}

		if time.Now().After(deadline) {
			return fmt.Errorf("%s did not become ready within %s; "+
				"run `habnutctl dev logs %s` to see what it is doing", service, timeout, service)
		}

		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-time.After(2 * time.Second):
		}
	}
}

// run executes a command, sending its output to out when one is given.
func run(ctx context.Context, out io.Writer, name string, args ...string) error {
	cmd := exec.CommandContext(ctx, name, args...)
	if out != nil {
		cmd.Stdout = out
		cmd.Stderr = out
	} else {
		cmd.Stdout = io.Discard
		cmd.Stderr = io.Discard
	}
	cmd.Env = os.Environ()
	return cmd.Run()
}
