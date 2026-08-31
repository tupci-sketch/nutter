//go:build linux

package installer

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
)

// platform groups the steps that differ between operating systems.
type platform struct {
	InstallDependencies []Step
	ConfigureServices   []Step
}

func defaultPaths() (install, data, config string) {
	return "/opt/habnut", "/var/lib/habnut", "/etc/habnut"
}

func serviceNames() []string {
	return []string{"habnut-emulator", "habnut-imager", "habnut-horizon", "nginx", "redis-server", "mariadb", "php8.3-fpm"}
}

func platformSteps() platform {
	return platform{
		InstallDependencies: []Step{
			{"Create system user", stepCreateUser},
			{"Install Java 21", stepInstallJava},
			{"Install MariaDB", stepInstallMariaDB},
			{"Install Redis", stepInstallRedis},
			{"Install Nginx", stepInstallNginx},
			{"Install PHP 8.3 and extensions", stepInstallPHP},
			{"Start database and cache", stepStartDataServices},
		},
		ConfigureServices: []Step{
			{"Set file ownership", stepChown},
			{"Configure Nginx", stepConfigureNginx},
			{"Obtain TLS certificate", stepObtainTLS},
			{"Install systemd units", stepInstallServices},
			{"Start emulator", stepStartEmulator},
			{"Start imager", stepStartImager},
			{"Start queue worker", stepStartHorizon},
			{"Start web server", stepStartNginx},
		},
	}
}

func stepCheckRequirements(_ *Config) error {
	if os.Geteuid() != 0 {
		return fmt.Errorf("install must run as root (try: sudo habnutctl install ...)")
	}
	for _, bin := range []string{"systemctl", "curl"} {
		if !have(bin) {
			return fmt.Errorf("required command not found: %s", bin)
		}
	}
	return nil
}

// aptInstall installs packages, refreshing the index once per run.
var aptUpdated bool

func aptInstall(pkgs ...string) error {
	if !have("apt-get") {
		return fmt.Errorf("apt-get not found: install these packages manually and re-run: %v", pkgs)
	}
	if !aptUpdated {
		_ = run("apt-get", "update", "-qq")
		aptUpdated = true
	}
	args := append([]string{"install", "-y", "-qq"}, pkgs...)
	cmd := exec.Command("apt-get", args...)
	cmd.Env = append(os.Environ(), "DEBIAN_FRONTEND=noninteractive")
	cmd.Stdout, cmd.Stderr = os.Stdout, os.Stderr
	return cmd.Run()
}

func stepCreateUser(_ *Config) error {
	if quiet("id", "habnut") == nil {
		return nil
	}
	return run("useradd", "--system", "--no-create-home", "--shell", "/usr/sbin/nologin", "habnut")
}

func stepInstallJava(_ *Config) error {
	if have("java") {
		return nil
	}
	return aptInstall("openjdk-21-jre-headless")
}

func stepInstallMariaDB(_ *Config) error {
	if have("mariadbd") || have("mysqld") {
		return nil
	}
	return aptInstall("mariadb-server")
}

func stepInstallRedis(_ *Config) error {
	if have("redis-server") {
		return nil
	}
	return aptInstall("redis-server")
}

func stepInstallNginx(_ *Config) error {
	if have("nginx") {
		return nil
	}
	return aptInstall("nginx")
}

func stepInstallPHP(_ *Config) error {
	if have("php8.3") {
		return nil
	}
	return aptInstall("php8.3-cli", "php8.3-fpm", "php8.3-mysql", "php8.3-redis",
		"php8.3-mbstring", "php8.3-xml", "php8.3-curl", "php8.3-zip",
		"php8.3-bcmath", "php8.3-intl", "php8.3-gd")
}

func stepStartDataServices(_ *Config) error {
	if err := run("systemctl", "enable", "--now", "mariadb"); err != nil {
		return err
	}
	return run("systemctl", "enable", "--now", "redis-server")
}

func runMySQLAdmin(sql string) error {
	return run("mysql", "-e", sql)
}

func stepChown(cfg *Config) error {
	for _, d := range []string{cfg.InstallPath, cfg.DataPath} {
		if err := run("chown", "-R", "habnut:habnut", d); err != nil {
			return err
		}
	}
	return os.Chmod(cfg.emulatorConfig(), 0o600)
}

func stepConfigureNginx(cfg *Config) error {
	var tlsBlock, listen string
	if cfg.SkipTLS {
		listen = "listen 80;"
	} else {
		listen = "listen 443 ssl;\n    http2 on;"
		tlsBlock = fmt.Sprintf(`
    ssl_certificate     /etc/letsencrypt/live/%s/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/%s/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;`,
			cfg.Domain, cfg.Domain)
	}

	redirect := ""
	if !cfg.SkipTLS {
		redirect = fmt.Sprintf(
			"server {\n    listen 80;\n    server_name %s;\n    return 301 https://$host$request_uri;\n}\n",
			cfg.Domain)
	}

	conf := fmt.Sprintf(`%sserver {
    %s
    server_name %s;
    root %s;
    index index.html;
%s
    add_header X-Frame-Options SAMEORIGIN always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;

    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 3600s;
    }

    location /assets/ {
        alias %s/assets/;
        expires 30d;
        access_log off;
    }

    location /cms {
        alias %s/public;
        try_files $uri $uri/ @cms;
    }

    location @cms {
        fastcgi_pass unix:/run/php/php8.3-fpm.sock;
        include fastcgi_params;
        fastcgi_param SCRIPT_FILENAME %s/public/index.php;
    }

    location / {
        try_files $uri /index.html;
    }
}
`, redirect, listen, cfg.Domain, cfg.clientDir(), tlsBlock, cfg.DataPath, cfg.cmsDir(), cfg.cmsDir())

	if err := os.MkdirAll("/etc/nginx/sites-enabled", 0o755); err != nil {
		return err
	}
	if err := os.WriteFile("/etc/nginx/sites-enabled/habnut.conf", []byte(conf), 0o644); err != nil {
		return err
	}
	return run("nginx", "-t")
}

func stepObtainTLS(cfg *Config) error {
	if cfg.SkipTLS {
		return nil
	}
	if !have("certbot") {
		if err := aptInstall("certbot", "python3-certbot-nginx"); err != nil {
			return err
		}
	}
	return run("certbot", "--nginx", "-d", cfg.Domain,
		"--non-interactive", "--agree-tos", "-m", cfg.AdminEmail, "--redirect")
}

func stepInstallServices(cfg *Config) error {
	units := map[string]string{
		"habnut-emulator.service": fmt.Sprintf(`[Unit]
Description=Habnut Emulator
After=network.target mariadb.service redis-server.service
Requires=mariadb.service

[Service]
Type=simple
User=habnut
WorkingDirectory=%s
ExecStart=/usr/bin/java -Xms512m -Xmx2g -jar %s --config %s
Restart=on-failure
RestartSec=5s
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
`, cfg.emulatorDir(), cfg.emulatorJar(), cfg.emulatorConfig()),

		"habnut-horizon.service": fmt.Sprintf(`[Unit]
Description=Habnut CMS Queue Worker
After=network.target redis-server.service

[Service]
Type=simple
User=habnut
WorkingDirectory=%s
ExecStart=/usr/bin/php artisan horizon
Restart=on-failure
RestartSec=5s

[Install]
WantedBy=multi-user.target
`, cfg.cmsDir()),

		// The imager is the launcher itself in a serving mode, so it needs
		// nothing installed beyond the binary already on the machine.
		"habnut-imager.service": fmt.Sprintf(`[Unit]
Description=Habnut Avatar and Badge Imager
After=network.target

[Service]
Type=simple
User=habnut
ExecStart=%s imager --listen 127.0.0.1:8081
Restart=on-failure
RestartSec=5s
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
`, selfPath()),
	}

	for name, body := range units {
		if err := os.WriteFile(filepath.Join("/etc/systemd/system", name), []byte(body), 0o644); err != nil {
			return err
		}
	}
	return run("systemctl", "daemon-reload")
}

func stepStartEmulator(_ *Config) error {
	return run("systemctl", "enable", "--now", "habnut-emulator")
}

// selfPath is where this binary lives, so the unit it writes keeps pointing at
// the launcher even when it was installed somewhere unusual.
func selfPath() string {
	path, err := os.Executable()
	if err != nil {
		return "/usr/local/bin/habnutctl"
	}
	return path
}

func stepStartImager(_ *Config) error {
	return run("systemctl", "enable", "--now", "habnut-imager")
}

func stepStartHorizon(_ *Config) error {
	return run("systemctl", "enable", "--now", "habnut-horizon")
}

func stepStartNginx(_ *Config) error {
	return run("systemctl", "enable", "--now", "nginx")
}
