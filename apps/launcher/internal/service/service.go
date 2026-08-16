// Package service controls the long-running Habnut services through whichever
// service manager the host operating system provides — systemd on Linux and the
// Windows Service Control Manager on Windows.
package service

// Names of the services habnutctl manages, in start order.
var Names = names()

// Status describes one service's current state.
type Status struct {
	Name   string
	State  string // "running", "stopped", or a manager-specific state
	Active bool
}

// Start starts a service, enabling it at boot where the manager supports it.
func Start(name string) error { return start(name) }

// Stop stops a running service.
func Stop(name string) error { return stop(name) }

// Restart restarts a service.
func Restart(name string) error { return restart(name) }

// Query reports the current state of a service.
func Query(name string) Status { return query(name) }

// Logs writes the last n lines of a service's log to stdout.
func Logs(name string, lines int, follow bool) error { return logs(name, lines, follow) }

// StatusAll reports the state of every managed service.
func StatusAll() []Status {
	out := make([]Status, 0, len(Names))
	for _, n := range Names {
		out = append(out, Query(n))
	}
	return out
}
