// Package manage is the hotel's control panel for the owner's own computer.
//
// It serves a page on 127.0.0.1 and turns what is clicked there into the
// server's own 'habnut' commands, run over SSH with the key the owner already
// logs in with. Nothing new is opened on the server: no port, no account, no
// token. The panel can only ask for the commands listed in actions below, with
// arguments checked here before they leave this machine and again by the
// server's script.
package manage

import (
	"crypto/rand"
	"crypto/subtle"
	_ "embed"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"os/exec"
	"regexp"
	"strings"
	"time"
)

//go:embed ui.html
var page []byte

// Config says which server to manage.
type Config struct {
	// Host is what is given to ssh: an alias from ~/.ssh/config, or user@address.
	Host string
	// Listen is the local address to serve on; port 0 picks a free one.
	Listen string
	// OpenBrowser opens the panel once it is listening.
	OpenBrowser bool
}

var (
	serviceName = regexp.MustCompile(`^(db|redis|emulator|cms|imager|nginx|cloudflared|prometheus|alertmanager|node-exporter)$`)
	backupName  = regexp.MustCompile(`^habnut-[0-9TZ]+\.sql\.gz$`)
	era         = regexp.MustCompile(`^(classic|modern)$`)
	buildName   = regexp.MustCompile(`^[A-Za-z0-9._-]{1,120}$`)
	hostShape   = regexp.MustCompile(`^[A-Za-z0-9._@:-]+$`)
)

// action is one thing the panel may ask the server to do.
type action struct {
	args    func(arg string) ([]string, error)
	timeout time.Duration
}

func fixed(args ...string) func(string) ([]string, error) {
	return func(string) ([]string, error) { return args, nil }
}

func withService(cmd string, extra ...string) func(string) ([]string, error) {
	return func(arg string) ([]string, error) {
		if arg == "" {
			return append([]string{cmd}, extra...), nil
		}
		if !serviceName.MatchString(arg) {
			return nil, fmt.Errorf("unknown service %q", arg)
		}
		return append(append([]string{cmd}, extra...), arg), nil
	}
}

var actions = map[string]action{
	"summary":  {fixed("summary"), 30 * time.Second},
	"status":   {fixed("status"), 30 * time.Second},
	"verify":   {fixed("verify"), 60 * time.Second},
	"logs":     {withService("logs", "--tail", "300", "--no-color"), time.Minute},
	"restart":  {withService("restart"), 5 * time.Minute},
	"backup":   {fixed("backup"), 15 * time.Minute},
	"backups":  {fixed("backups"), 30 * time.Second},
	"update":   {fixed("update"), 45 * time.Minute},
	"rollback": {fixed("rollback"), 15 * time.Minute},
	"assets":   {fixed("assets", "status"), time.Minute},
	"builds":   {fixed("assets", "builds"), time.Minute},
	"furni":    {fixed("assets", "furni"), 60 * time.Minute},
	"furniall": {fixed("assets", "furni", "all"), 180 * time.Minute},
	"sync":     {fixed("furni", "sync"), 15 * time.Minute},
	"fetch": {func(arg string) ([]string, error) {
		if !buildName.MatchString(arg) {
			return nil, fmt.Errorf("not a build name")
		}
		return []string{"assets", "fetch", arg}, nil
	}, 90 * time.Minute},
}

// Run serves the panel until the process is stopped.
func Run(cfg Config) error {
	if !hostShape.MatchString(cfg.Host) {
		return fmt.Errorf("%q is not a host ssh can be given", cfg.Host)
	}
	if _, err := exec.LookPath("ssh"); err != nil {
		return errors.New("ssh is not installed on this computer")
	}

	// Every request must carry this, so no web page open in the same browser
	// can drive the panel: it cannot read the token, and a request carrying a
	// custom header from another origin is refused by the browser before it is
	// sent.
	token, err := randomToken()
	if err != nil {
		return err
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		if r.URL.Path != "/" {
			http.NotFound(w, r)
			return
		}
		w.Header().Set("Content-Type", "text/html; charset=utf-8")
		w.Header().Set("Cache-Control", "no-store")
		w.Header().Set("Content-Security-Policy", "default-src 'self'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; frame-ancestors 'none'")
		_, _ = w.Write([]byte(strings.Replace(string(page), "__TOKEN__", token, 1)))
	})
	mux.HandleFunc("/api/info", guarded(token, func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]string{"host": cfg.Host})
	}))
	mux.HandleFunc("/api/run", guarded(token, func(w http.ResponseWriter, r *http.Request) {
		name := r.URL.Query().Get("action")
		a, ok := actions[name]
		if !ok {
			http.Error(w, "unknown action", http.StatusBadRequest)
			return
		}
		args, err := a.args(r.URL.Query().Get("arg"))
		if err != nil {
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		stream(w, r, cfg.Host, a.timeout, nil, args...)
	}))
	mux.HandleFunc("/api/assets", guarded(token, func(w http.ResponseWriter, r *http.Request) {
		e := r.URL.Query().Get("era")
		if !era.MatchString(e) {
			http.Error(w, "era must be classic or modern", http.StatusBadRequest)
			return
		}
		stream(w, r, cfg.Host, time.Hour, r.Body, "assets", "receive", e)
	}))
	mux.HandleFunc("/api/backup", guarded(token, func(w http.ResponseWriter, r *http.Request) {
		name := r.URL.Query().Get("name")
		if !backupName.MatchString(name) {
			http.Error(w, "not a backup name", http.StatusBadRequest)
			return
		}
		cmd := sshCommand(r, cfg.Host, "backup-fetch", name)
		out, err := cmd.StdoutPipe()
		if err != nil {
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		var stderr strings.Builder
		cmd.Stderr = &stderr
		if err := cmd.Start(); err != nil {
			http.Error(w, err.Error(), http.StatusBadGateway)
			return
		}
		w.Header().Set("Content-Type", "application/gzip")
		w.Header().Set("Content-Disposition", `attachment; filename="`+name+`"`)
		_, _ = io.Copy(w, out)
		if err := cmd.Wait(); err != nil {
			fmt.Fprintf(os.Stderr, "backup download failed: %v %s\n", err, stderr.String())
		}
	}))

	ln, err := net.Listen("tcp", cfg.Listen)
	if err != nil {
		return err
	}
	url := "http://" + ln.Addr().String() + "/"
	fmt.Printf("Habnut control panel for %s\n\n  %s\n\nLeave this window open while you use it; Ctrl+C closes it.\n", cfg.Host, url)
	if cfg.OpenBrowser {
		openBrowser(url)
	}
	srv := &http.Server{Handler: mux, ReadHeaderTimeout: 10 * time.Second}
	return srv.Serve(ln)
}

func guarded(token string, h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		got := r.Header.Get("X-Habnut-Token")
		if got == "" {
			got = r.URL.Query().Get("token") // downloads are plain links
		}
		if subtle.ConstantTimeCompare([]byte(got), []byte(token)) != 1 {
			http.Error(w, "forbidden", http.StatusForbidden)
			return
		}
		h(w, r)
	}
}

// sshCommand runs 'sudo habnut <args>' on the server. BatchMode means ssh never
// waits on a prompt nobody can see: a missing key fails at once, with a reason.
// A key with a passphrase works through the desktop's own key agent.
func sshCommand(r *http.Request, host string, args ...string) *exec.Cmd {
	full := append([]string{
		"-o", "BatchMode=yes",
		"-o", "ConnectTimeout=15",
		"-o", "ServerAliveInterval=30",
		host, "sudo", "-n", "/usr/local/bin/habnut",
	}, args...)
	return exec.CommandContext(r.Context(), "ssh", full...)
}

// stream runs a command on the server and sends its output as it arrives.
func stream(w http.ResponseWriter, r *http.Request, host string, timeout time.Duration, stdin io.Reader, args ...string) {
	flusher, _ := w.(http.Flusher)
	w.Header().Set("Content-Type", "text/plain; charset=utf-8")
	w.Header().Set("Cache-Control", "no-store")
	w.Header().Set("X-Content-Type-Options", "nosniff")

	cmd := sshCommand(r, host, args...)
	cmd.WaitDelay = 5 * time.Second
	if stdin != nil {
		cmd.Stdin = stdin
	}
	pw := &flushWriter{w: w, f: flusher}
	cmd.Stdout, cmd.Stderr = pw, pw

	timer := time.AfterFunc(timeout, func() {
		if cmd.Process != nil {
			_ = cmd.Process.Kill()
		}
	})
	defer timer.Stop()

	err := cmd.Run()
	code := 0
	if err != nil {
		var exit *exec.ExitError
		if errors.As(err, &exit) {
			code = exit.ExitCode()
		} else {
			code = -1
			fmt.Fprintf(pw, "\n%v\n", err)
		}
		if code == 255 {
			fmt.Fprintf(pw, "\nCould not reach %s over ssh. Check that 'ssh %s' works in a terminal.\n", host, host)
		}
	}
	fmt.Fprintf(pw, "\n\x00exit:%d\n", code)
}

type flushWriter struct {
	w io.Writer
	f http.Flusher
}

func (fw *flushWriter) Write(p []byte) (int, error) {
	n, err := fw.w.Write(p)
	if fw.f != nil {
		fw.f.Flush()
	}
	return n, err
}

func randomToken() (string, error) {
	b := make([]byte, 24)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

func openBrowser(url string) {
	for _, c := range [][]string{{"xdg-open", url}, {"open", url}, {"rundll32", "url.dll,FileProtocolHandler", url}} {
		if _, err := exec.LookPath(c[0]); err == nil {
			_ = exec.Command(c[0], c[1:]...).Start()
			return
		}
	}
}
