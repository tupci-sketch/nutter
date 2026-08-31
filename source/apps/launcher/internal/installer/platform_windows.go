//go:build windows

package installer

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

// platform groups the steps that differ between operating systems.
type platform struct {
	InstallDependencies []Step
	ConfigureServices   []Step
}

func defaultPaths() (install, data, config string) {
	root := os.Getenv("ProgramData")
	if root == "" {
		root = `C:\ProgramData`
	}
	base := filepath.Join(root, "Habnut")
	return filepath.Join(base, "app"), filepath.Join(base, "data"), filepath.Join(base, "config")
}

func serviceNames() []string {
	return []string{"HabnutEmulator", "HabnutHorizon", "nginx", "MariaDB", "Redis"}
}

func platformSteps() platform {
	return platform{
		InstallDependencies: []Step{
			{"Install Java 21", stepInstallJava},
			{"Install MariaDB", stepInstallMariaDB},
			{"Install Redis", stepInstallRedis},
			{"Install Nginx", stepInstallNginx},
			{"Install PHP 8.3", stepInstallPHP},
			{"Start database and cache", stepStartDataServices},
		},
		ConfigureServices: []Step{
			{"Configure Nginx", stepConfigureNginx},
			{"Register Windows services", stepInstallServices},
			{"Start emulator", stepStartEmulator},
			{"Start queue worker", stepStartHorizon},
			{"Start web server", stepStartNginx},
		},
	}
}

func stepCheckRequirements(_ *Config) error {
	if !isAdmin() {
		return fmt.Errorf("install must run from an elevated Administrator prompt")
	}
	if !have("winget") {
		return fmt.Errorf("winget not found; install the App Installer package from the " +
			"Microsoft Store, or install Java 21, MariaDB, Redis, Nginx and PHP 8.3 manually and re-run")
	}
	return nil
}

// isAdmin reports whether the process holds administrative rights.  Opening the
// physical drive handle succeeds only for elevated processes.
func isAdmin() bool {
	f, err := os.Open(`\\.\PHYSICALDRIVE0`)
	if err != nil {
		return false
	}
	_ = f.Close()
	return true
}

// wingetInstall installs a package by its winget identifier, treating an
// already-installed package as success.
func wingetInstall(id string) error {
	cmd := exec.Command("winget", "install", "--id", id, "--exact",
		"--silent", "--accept-package-agreements", "--accept-source-agreements")
	out, err := cmd.CombinedOutput()
	if err == nil {
		return nil
	}
	text := string(out)
	if strings.Contains(text, "already installed") || strings.Contains(text, "No newer package") {
		return nil
	}
	return fmt.Errorf("winget install %s: %w\n%s", id, err, text)
}

func stepInstallJava(_ *Config) error {
	if have("java") {
		return nil
	}
	return wingetInstall("EclipseAdoptium.Temurin.21.JRE")
}

func stepInstallMariaDB(_ *Config) error {
	if have("mysql") {
		return nil
	}
	return wingetInstall("MariaDB.Server")
}

func stepInstallRedis(_ *Config) error {
	if have("redis-server") {
		return nil
	}
	// Redis has no official Windows build; Memurai is the supported
	// Redis-compatible service for Windows hosts.
	return wingetInstall("Memurai.MemuraiDeveloper")
}

func stepInstallNginx(_ *Config) error {
	if have("nginx") {
		return nil
	}
	return wingetInstall("nginx.nginx")
}

func stepInstallPHP(_ *Config) error {
	if have("php") {
		return nil
	}
	return wingetInstall("PHP.PHP.8.3")
}

func stepStartDataServices(_ *Config) error {
	for _, svc := range []string{"MariaDB", "Memurai"} {
		_ = quiet("sc", "start", svc)
	}
	return nil
}

func runMySQLAdmin(sql string) error {
	return run("mysql", "-u", "root", "-e", sql)
}

func stepConfigureNginx(cfg *Config) error {
	conf := fmt.Sprintf(`worker_processes auto;
events { worker_connections 1024; }
http {
    include       mime.types;
    default_type  application/octet-stream;
    sendfile on;
    keepalive_timeout 65;

    server {
        listen 80;
        server_name %s;
        root %s;
        index index.html;

        add_header X-Frame-Options SAMEORIGIN always;
        add_header X-Content-Type-Options nosniff always;

        location /ws {
            proxy_pass http://127.0.0.1:8080;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection "upgrade";
            proxy_set_header Host $host;
            proxy_read_timeout 3600s;
        }

        location /assets/ {
            alias %s/assets/;
            expires 30d;
        }

        location /cms {
            alias %s/public;
            try_files $uri $uri/ @cms;
        }

        location @cms {
            fastcgi_pass 127.0.0.1:9000;
            include fastcgi_params;
            fastcgi_param SCRIPT_FILENAME %s/public/index.php;
        }

        location / {
            try_files $uri /index.html;
        }
    }
}
`, cfg.Domain,
		toNginxPath(cfg.clientDir()),
		toNginxPath(cfg.DataPath),
		toNginxPath(cfg.cmsDir()),
		toNginxPath(cfg.cmsDir()))

	dest := filepath.Join(cfg.ConfigPath, "nginx.conf")
	return os.WriteFile(dest, []byte(conf), 0o644)
}

// toNginxPath converts a Windows path to the forward-slash form nginx expects.
func toNginxPath(p string) string { return strings.ReplaceAll(p, `\`, "/") }

func stepInstallServices(cfg *Config) error {
	// sc.exe create requires binPath as a single quoted string.
	emulator := fmt.Sprintf(`"%s" -Xms512m -Xmx2g -jar "%s" --config "%s"`,
		javaPath(), cfg.emulatorJar(), cfg.emulatorConfig())
	if err := createService("HabnutEmulator", "Habnut Emulator", emulator); err != nil {
		return err
	}

	horizon := fmt.Sprintf(`"%s" "%s" horizon`, phpPath(), filepath.Join(cfg.cmsDir(), "artisan"))
	return createService("HabnutHorizon", "Habnut CMS Queue Worker", horizon)
}

func createService(name, display, binPath string) error {
	// Remove any prior registration so re-installs are idempotent.
	_ = quiet("sc", "delete", name)
	if err := run("sc", "create", name,
		"binPath=", binPath,
		"DisplayName=", display,
		"start=", "auto"); err != nil {
		return fmt.Errorf("register service %s: %w", name, err)
	}
	return run("sc", "failure", name, "reset=", "60", "actions=", "restart/5000")
}

func javaPath() string {
	if p, err := exec.LookPath("java"); err == nil {
		return p
	}
	return "java.exe"
}

func phpPath() string {
	if p, err := exec.LookPath("php"); err == nil {
		return p
	}
	return "php.exe"
}

func stepStartEmulator(_ *Config) error { return run("sc", "start", "HabnutEmulator") }
func stepStartHorizon(_ *Config) error  { return run("sc", "start", "HabnutHorizon") }

func stepStartNginx(cfg *Config) error {
	nginx, err := exec.LookPath("nginx")
	if err != nil {
		return fmt.Errorf("nginx not found on PATH: %w", err)
	}
	return run(nginx, "-c", filepath.Join(cfg.ConfigPath, "nginx.conf"))
}
