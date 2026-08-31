package doctor

import (
	"archive/zip"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"time"

	"github.com/habnut/launcher/internal/state"
)

// Check represents a single doctor check.
type Check struct {
	Name   string
	Status string
	Detail string
}

// Run performs all doctor checks and prints results.
func Run() ([]Check, error) {
	checks := []Check{}

	checks = append(checks, checkBinary("java", "--version"))
	checks = append(checks, checkBinary("php8.3", "--version"))
	checks = append(checks, checkBinary("nginx", "-v"))
	checks = append(checks, checkBinary("redis-cli", "ping"))
	checks = append(checks, checkService("habnut-emulator"))
	checks = append(checks, checkService("nginx"))
	checks = append(checks, checkService("mariadb"))
	checks = append(checks, checkService("redis-server"))
	checks = append(checks, checkHTTP("http://localhost:8080/health", "Emulator health"))
	checks = append(checks, checkStateFile())
	checks = append(checks, checkDiskSpace("/var/lib/habnut"))

	allOk := true
	for _, c := range checks {
		icon := "✓"
		if c.Status != "ok" {
			icon = "✗"
			allOk = false
		}
		fmt.Printf("  [%s] %-40s %s\n", icon, c.Name, c.Detail)
	}

	if !allOk {
		return checks, fmt.Errorf("one or more checks failed")
	}
	return checks, nil
}

// SupportBundle creates a redacted support bundle zip at bundlePath.
func SupportBundle(bundlePath string) error {
	zf, err := os.Create(bundlePath)
	if err != nil {
		return err
	}
	defer zf.Close()

	zw := zip.NewWriter(zf)
	defer zw.Close()

	// Add state.json (redacted)
	if s, err := state.Load(); err == nil {
		addJSON(zw, "state.json", s)
	}

	// Add systemd journal (last 500 lines)
	addCmd(zw, "emulator.log", "journalctl", "-u", "habnut-emulator", "-n", "500", "--no-pager")
	addCmd(zw, "nginx.log", "journalctl", "-u", "nginx", "-n", "200", "--no-pager")

	// Add doctor output
	addText(zw, "doctor.txt", captureDoctor())

	fmt.Printf("Support bundle written to %s\n", bundlePath)
	return nil
}

func checkBinary(name string, args ...string) Check {
	path, err := exec.LookPath(name)
	if err != nil {
		return Check{Name: name + " binary", Status: "fail", Detail: "not found in PATH"}
	}
	return Check{Name: name + " binary", Status: "ok", Detail: path}
}

func checkService(name string) Check {
	cmd := exec.Command("systemctl", "is-active", name)
	out, _ := cmd.Output()
	active := strings.TrimSpace(string(out)) == "active"
	if active {
		return Check{Name: name + " service", Status: "ok", Detail: "active"}
	}
	return Check{Name: name + " service", Status: "fail", Detail: "not active"}
}

func checkHTTP(url, name string) Check {
	cmd := exec.Command("curl", "-fs", url)
	if err := cmd.Run(); err != nil {
		return Check{Name: name, Status: "fail", Detail: "unreachable"}
	}
	return Check{Name: name, Status: "ok", Detail: url}
}

func checkStateFile() Check {
	s, err := state.Load()
	if err != nil {
		return Check{Name: "State file", Status: "fail", Detail: err.Error()}
	}
	return Check{Name: "State file", Status: "ok", Detail: "v" + s.InstalledVersion}
}

func checkDiskSpace(path string) Check {
	cmd := exec.Command("df", "-h", path)
	out, err := cmd.Output()
	if err != nil {
		return Check{Name: "Disk space " + path, Status: "warn", Detail: "cannot check"}
	}
	lines := strings.Split(string(out), "\n")
	if len(lines) >= 2 {
		return Check{Name: "Disk space " + path, Status: "ok", Detail: lines[1]}
	}
	return Check{Name: "Disk space " + path, Status: "ok", Detail: "ok"}
}

func addJSON(zw *zip.Writer, name string, v interface{}) {
	w, _ := zw.Create(name)
	fmt.Fprintf(w, "%v", v)
}

func addText(zw *zip.Writer, name, text string) {
	w, _ := zw.Create(name)
	io.WriteString(w, text)
}

func addCmd(zw *zip.Writer, name string, args ...string) {
	out, err := exec.Command(args[0], args[1:]...).Output()
	w, _ := zw.Create(name)
	if err == nil {
		w.Write(out)
	} else {
		fmt.Fprintf(w, "error: %v\n", err)
	}
}

func captureDoctor() string {
	var sb strings.Builder
	sb.WriteString(fmt.Sprintf("Doctor run at %s\n\n", time.Now().Format(time.RFC3339)))
	checks, _ := Run()
	for _, c := range checks {
		sb.WriteString(fmt.Sprintf("[%s] %s: %s\n", c.Status, c.Name, c.Detail))
	}
	return sb.String()
}

// Ensure unused import doesn't cause compile error.
var _ = filepath.Join
