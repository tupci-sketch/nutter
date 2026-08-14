package installer

import (
	"fmt"
	"os"
	"os/exec"
	"time"

	"github.com/habnut/launcher/internal/state"
)

// Step describes one installation step.
type Step struct {
	Name string
	Run  func(cfg *Config) error
}

// Config holds installer configuration collected from the user.
type Config struct {
	InstallPath string
	DbHost      string
	DbPort      string
	DbName      string
	DbUser      string
	DbPass      string
	RedisHost   string
	RedisPort   string
	Domain      string
	AdminEmail  string
	Version     string
}

// DefaultConfig returns defaults for all config fields.
func DefaultConfig() *Config {
	return &Config{
		InstallPath: "/opt/habnut",
		DbHost:      "127.0.0.1",
		DbPort:      "3306",
		DbName:      "habnut",
		DbUser:      "habnut",
		RedisHost:   "127.0.0.1",
		RedisPort:   "6379",
		Version:     "latest",
	}
}

// Steps is the 28-step installation sequence.
var Steps = []Step{
	{"Check system requirements", stepCheckRequirements},
	{"Create system user habnut", stepCreateUser},
	{"Create directory structure", stepCreateDirs},
	{"Install Java 21 (if missing)", stepInstallJava},
	{"Install MariaDB (if missing)", stepInstallMariaDB},
	{"Install Redis (if missing)", stepInstallRedis},
	{"Install Nginx (if missing)", stepInstallNginx},
	{"Install PHP 8.3 + extensions", stepInstallPHP},
	{"Install Composer", stepInstallComposer},
	{"Install Node.js + pnpm", stepInstallNode},
	{"Create MariaDB database and user", stepConfigureDatabase},
	{"Download emulator JAR", stepDownloadEmulator},
	{"Configure emulator", stepConfigureEmulator},
	{"Run Flyway migrations (V1–V9)", stepRunMigrations},
	{"Download and build CMS", stepBuildCMS},
	{"Configure CMS (.env)", stepConfigureCMS},
	{"Download and build client", stepBuildClient},
	{"Configure Nginx", stepConfigureNginx},
	{"Obtain TLS certificate (Certbot)", stepObtainTLS},
	{"Install systemd service units", stepInstallServices},
	{"Start Redis", stepStartRedis},
	{"Start MariaDB", stepStartMariaDB},
	{"Start emulator", stepStartEmulator},
	{"Start CMS queue worker (Horizon)", stepStartHorizon},
	{"Start Nginx", stepStartNginx},
	{"Run health checks", stepHealthCheck},
	{"Seed initial catalogue and permissions", stepSeedData},
	{"Write state file", stepWriteState},
}

func Run(cfg *Config, progress func(step, total int, name string)) error {
	for i, step := range Steps {
		progress(i+1, len(Steps), step.Name)
		if err := step.Run(cfg); err != nil {
			return fmt.Errorf("step %d (%s): %w", i+1, step.Name, err)
		}
	}
	return nil
}

func run(name string, args ...string) error {
	cmd := exec.Command(name, args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

func stepCheckRequirements(cfg *Config) error {
	for _, bin := range []string{"curl", "unzip", "systemctl"} {
		if _, err := exec.LookPath(bin); err != nil {
			return fmt.Errorf("required command not found: %s", bin)
		}
	}
	return nil
}

func stepCreateUser(_ *Config) error {
	if err := run("id", "habnut"); err == nil {
		return nil // already exists
	}
	return run("useradd", "--system", "--no-create-home", "--shell", "/usr/sbin/nologin", "habnut")
}

func stepCreateDirs(cfg *Config) error {
	dirs := []string{
		cfg.InstallPath,
		cfg.InstallPath + "/emulator",
		cfg.InstallPath + "/cms",
		cfg.InstallPath + "/client",
		cfg.InstallPath + "/logs",
		"/var/lib/habnut",
		"/etc/habnut",
	}
	for _, d := range dirs {
		if err := os.MkdirAll(d, 0755); err != nil {
			return err
		}
	}
	return nil
}

func stepInstallJava(_ *Config) error {
	if _, err := exec.LookPath("java"); err == nil {
		return nil
	}
	return run("apt-get", "install", "-y", "openjdk-21-jre-headless")
}

func stepInstallMariaDB(_ *Config) error {
	if _, err := exec.LookPath("mysqld"); err == nil {
		return nil
	}
	return run("apt-get", "install", "-y", "mariadb-server")
}

func stepInstallRedis(_ *Config) error {
	if _, err := exec.LookPath("redis-server"); err == nil {
		return nil
	}
	return run("apt-get", "install", "-y", "redis-server")
}

func stepInstallNginx(_ *Config) error {
	if _, err := exec.LookPath("nginx"); err == nil {
		return nil
	}
	return run("apt-get", "install", "-y", "nginx")
}

func stepInstallPHP(_ *Config) error {
	if _, err := exec.LookPath("php8.3"); err == nil {
		return nil
	}
	pkgs := []string{
		"apt-get", "install", "-y",
		"php8.3", "php8.3-fpm", "php8.3-mysql", "php8.3-redis",
		"php8.3-mbstring", "php8.3-xml", "php8.3-curl", "php8.3-zip",
		"php8.3-bcmath", "php8.3-intl", "php8.3-gd",
	}
	return run(pkgs[0], pkgs[1:]...)
}

func stepInstallComposer(_ *Config) error {
	if _, err := exec.LookPath("composer"); err == nil {
		return nil
	}
	return run("bash", "-c",
		`curl -sS https://getcomposer.org/installer | php -- --install-dir=/usr/local/bin --filename=composer`)
}

func stepInstallNode(_ *Config) error {
	if _, err := exec.LookPath("node"); err != nil {
		if err := run("apt-get", "install", "-y", "nodejs", "npm"); err != nil {
			return err
		}
	}
	if _, err := exec.LookPath("pnpm"); err == nil {
		return nil
	}
	return run("npm", "install", "-g", "pnpm@8")
}

func stepConfigureDatabase(cfg *Config) error {
	sql := fmt.Sprintf(`
CREATE DATABASE IF NOT EXISTS %s CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '%s'@'localhost' IDENTIFIED BY '%s';
GRANT ALL PRIVILEGES ON %s.* TO '%s'@'localhost';
FLUSH PRIVILEGES;
`, cfg.DbName, cfg.DbUser, cfg.DbPass, cfg.DbName, cfg.DbUser)
	cmd := exec.Command("mysql", "-e", sql)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

func stepDownloadEmulator(cfg *Config) error {
	// Download from configured release URL; in production this points to the
	// project's release artifact store.
	return run("curl", "-fsSL", "-o", cfg.InstallPath+"/emulator/habnut-emulator.jar",
		"https://releases.habnut.internal/emulator/latest/habnut-emulator.jar")
}

func stepConfigureEmulator(cfg *Config) error {
	config := fmt.Sprintf(`
db.host=%s
db.port=%s
db.name=%s
db.user=%s
db.pass=%s
redis.host=%s
redis.port=%s
server.port=8080
`, cfg.DbHost, cfg.DbPort, cfg.DbName, cfg.DbUser, cfg.DbPass, cfg.RedisHost, cfg.RedisPort)
	return os.WriteFile("/etc/habnut/emulator.properties", []byte(config), 0600)
}

func stepRunMigrations(cfg *Config) error {
	return run("java", "-jar", cfg.InstallPath+"/emulator/habnut-emulator.jar", "--migrate-only")
}

func stepBuildCMS(cfg *Config) error {
	if err := run("composer", "install", "--no-dev", "--optimize-autoloader",
		"--working-dir", cfg.InstallPath+"/cms"); err != nil {
		return err
	}
	return run("php", cfg.InstallPath+"/cms/artisan", "config:cache")
}

func stepConfigureCMS(cfg *Config) error {
	env := fmt.Sprintf(`APP_NAME=Habnut
APP_ENV=production
APP_KEY=
APP_DEBUG=false
APP_URL=https://%s
DB_CONNECTION=mariadb
DB_HOST=%s
DB_PORT=%s
DB_DATABASE=%s
DB_USERNAME=%s
DB_PASSWORD=%s
REDIS_HOST=%s
REDIS_PORT=%s
SESSION_DRIVER=redis
QUEUE_CONNECTION=redis
CACHE_STORE=redis
MAIL_FROM_ADDRESS=no-reply@%s
`, cfg.Domain, cfg.DbHost, cfg.DbPort, cfg.DbName, cfg.DbUser, cfg.DbPass,
		cfg.RedisHost, cfg.RedisPort, cfg.Domain)
	if err := os.WriteFile(cfg.InstallPath+"/cms/.env", []byte(env), 0600); err != nil {
		return err
	}
	return run("php", cfg.InstallPath+"/cms/artisan", "key:generate", "--force")
}

func stepBuildClient(cfg *Config) error {
	return run("sh", "-c",
		fmt.Sprintf("cd %s/client && pnpm install && pnpm build", cfg.InstallPath))
}

func stepConfigureNginx(cfg *Config) error {
	cfg2 := fmt.Sprintf(`server {
    listen 80;
    server_name %s;
    return 301 https://$host$request_uri;
}
server {
    listen 443 ssl http2;
    server_name %s;
    root %s/client/dist;
    ssl_certificate     /etc/letsencrypt/live/%s/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/%s/privkey.pem;
    ssl_protocols TLSv1.2 TLSv1.3;
    add_header Strict-Transport-Security "max-age=63072000; includeSubDomains; preload" always;
    add_header X-Frame-Options DENY always;
    add_header X-Content-Type-Options nosniff always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;
    location /ws {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_read_timeout 3600s;
    }
    location /api {
        try_files $uri /index.php?$query_string;
        fastcgi_pass unix:/run/php/php8.3-fpm.sock;
        fastcgi_index index.php;
        include fastcgi_params;
        fastcgi_param SCRIPT_FILENAME %s/cms/public/index.php;
    }
    location / {
        try_files $uri /index.html;
    }
}
`, cfg.Domain, cfg.Domain, cfg.InstallPath, cfg.Domain, cfg.Domain, cfg.InstallPath)
	return os.WriteFile("/etc/nginx/sites-enabled/habnut.conf", []byte(cfg2), 0644)
}

func stepObtainTLS(cfg *Config) error {
	return run("certbot", "--nginx", "-d", cfg.Domain,
		"--non-interactive", "--agree-tos", "-m", cfg.AdminEmail)
}

func stepInstallServices(cfg *Config) error {
	unit := fmt.Sprintf(`[Unit]
Description=Habnut Emulator
After=network.target mariadb.service redis.service

[Service]
User=habnut
ExecStart=/usr/bin/java -jar %s/emulator/habnut-emulator.jar --config /etc/habnut/emulator.properties
Restart=on-failure
RestartSec=5s
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
`, cfg.InstallPath)
	if err := os.WriteFile("/etc/systemd/system/habnut-emulator.service", []byte(unit), 0644); err != nil {
		return err
	}
	return run("systemctl", "daemon-reload")
}

func stepStartRedis(_ *Config) error  { return run("systemctl", "enable", "--now", "redis-server") }
func stepStartMariaDB(_ *Config) error { return run("systemctl", "enable", "--now", "mariadb") }
func stepStartEmulator(_ *Config) error {
	return run("systemctl", "enable", "--now", "habnut-emulator")
}
func stepStartHorizon(cfg *Config) error {
	return run("php", cfg.InstallPath+"/cms/artisan", "horizon:install")
}
func stepStartNginx(_ *Config) error { return run("systemctl", "enable", "--now", "nginx") }

func stepHealthCheck(cfg *Config) error {
	for i := 0; i < 10; i++ {
		err := run("curl", "-fs", "http://localhost:8080/health")
		if err == nil {
			return nil
		}
		time.Sleep(2 * time.Second)
	}
	return fmt.Errorf("emulator health check failed after 10 attempts")
}

func stepSeedData(cfg *Config) error {
	return run("php", cfg.InstallPath+"/cms/artisan", "db:seed", "--class=InitialSeeder")
}

func stepWriteState(cfg *Config) error {
	now := time.Now()
	s := &state.State{
		InstalledVersion: cfg.Version,
		InstallPath:      cfg.InstallPath,
		InstalledAt:      now,
		Services:         []string{"habnut-emulator", "nginx", "redis-server", "mariadb", "php8.3-fpm"},
		DbMigrationLevel: 9,
	}
	return state.Save(s)
}
