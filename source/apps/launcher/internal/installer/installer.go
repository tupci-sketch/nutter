package installer

import (
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"github.com/habnut/launcher/internal/payload"
	"github.com/habnut/launcher/internal/seed"
	"github.com/habnut/launcher/internal/state"
)

// Step describes one installation step.
type Step struct {
	Name string
	Run  func(cfg *Config) error
}

// Config holds installation settings.  Paths default to the conventional
// location for the host operating system.
type Config struct {
	InstallPath string
	DataPath    string
	ConfigPath  string
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
	SkipTLS     bool
}

// DefaultConfig returns platform-appropriate defaults.
func DefaultConfig() *Config {
	c := &Config{
		DbHost:    "127.0.0.1",
		DbPort:    "3306",
		DbName:    "habnut",
		DbUser:    "habnut",
		RedisHost: "127.0.0.1",
		RedisPort: "6379",
		Version:   "1.0.0",
	}
	c.InstallPath, c.DataPath, c.ConfigPath = defaultPaths()
	return c
}

// Paths to the deployed component trees.
func (c *Config) emulatorDir() string { return filepath.Join(c.InstallPath, "emulator") }
func (c *Config) cmsDir() string      { return filepath.Join(c.InstallPath, "cms") }
func (c *Config) clientDir() string   { return filepath.Join(c.InstallPath, "client") }
func (c *Config) emulatorJar() string {
	return filepath.Join(c.emulatorDir(), "habnut-emulator.jar")
}
func (c *Config) emulatorConfig() string {
	return filepath.Join(c.ConfigPath, "emulator.properties")
}

// Steps returns the full installation sequence for the host platform.  Shared
// steps deploy the components carried inside this binary; platform steps
// install runtime dependencies and register services.
func Steps() []Step {
	p := platformSteps()

	steps := []Step{
		{"Check system requirements", stepCheckRequirements},
		{"Verify embedded components", stepVerifyPayload},
		{"Create directory structure", stepCreateDirs},
	}
	steps = append(steps, p.InstallDependencies...)
	steps = append(steps,
		Step{"Create database and user", stepConfigureDatabase},
		Step{"Deploy emulator", stepDeployEmulator},
		Step{"Deploy CMS", stepDeployCMS},
		Step{"Deploy client", stepDeployClient},
		Step{"Configure emulator", stepConfigureEmulator},
		Step{"Configure CMS", stepConfigureCMS},
		Step{"Run database migrations", stepRunMigrations},
		Step{"Generate CMS application key", stepCmsKey},
		Step{"Seed catalogue, ranks and permissions", stepSeedData},
	)
	steps = append(steps, p.ConfigureServices...)
	steps = append(steps,
		Step{"Run health checks", stepHealthCheck},
		Step{"Write state file", stepWriteState},
	)
	return steps
}

// Run executes every step in order, reporting progress to the callback.
func Run(cfg *Config, progress func(step, total int, name string)) error {
	steps := Steps()
	for i, step := range steps {
		progress(i+1, len(steps), step.Name)
		if err := step.Run(cfg); err != nil {
			return fmt.Errorf("step %d (%s): %w", i+1, step.Name, err)
		}
	}
	return nil
}

// ─── shared steps ───────────────────────────────────────────────────────────

func stepVerifyPayload(_ *Config) error {
	if !payload.Available() {
		return payload.ErrNotBundled
	}
	return nil
}

func stepCreateDirs(cfg *Config) error {
	for _, d := range []string{
		cfg.InstallPath, cfg.emulatorDir(), cfg.cmsDir(), cfg.clientDir(),
		filepath.Join(cfg.InstallPath, "logs"), filepath.Join(cfg.InstallPath, "backups"),
		cfg.DataPath, cfg.ConfigPath,
	} {
		if err := os.MkdirAll(d, 0o755); err != nil {
			return err
		}
	}
	return nil
}

func stepDeployEmulator(cfg *Config) error {
	return payload.Emulator(cfg.emulatorJar())
}

func stepDeployCMS(cfg *Config) error {
	return payload.ExtractCMS(cfg.cmsDir())
}

func stepDeployClient(cfg *Config) error {
	return payload.ExtractClient(cfg.clientDir())
}

func stepConfigureDatabase(cfg *Config) error {
	sql := fmt.Sprintf(
		"CREATE DATABASE IF NOT EXISTS %s CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"+
			"CREATE USER IF NOT EXISTS '%s'@'localhost' IDENTIFIED BY '%s';"+
			"GRANT ALL PRIVILEGES ON %s.* TO '%s'@'localhost';"+
			"FLUSH PRIVILEGES;",
		cfg.DbName, cfg.DbUser, cfg.DbPass, cfg.DbName, cfg.DbUser)
	return runMySQLAdmin(sql)
}

func stepConfigureEmulator(cfg *Config) error {
	props := fmt.Sprintf(`# Habnut emulator configuration
DB_URL=jdbc:mariadb://%s:%s/%s
DB_USER=%s
DB_PASSWORD=%s
REDIS_HOST=%s
REDIS_PORT=%s
SERVER_PORT=8080
METRICS_PORT=9090
`, cfg.DbHost, cfg.DbPort, cfg.DbName, cfg.DbUser, cfg.DbPass, cfg.RedisHost, cfg.RedisPort)
	return os.WriteFile(cfg.emulatorConfig(), []byte(props), 0o600)
}

func stepConfigureCMS(cfg *Config) error {
	scheme := "https"
	if cfg.SkipTLS {
		scheme = "http"
	}
	env := fmt.Sprintf(`APP_NAME=Habnut
APP_ENV=production
APP_KEY=
APP_DEBUG=false
APP_URL=%s://%s

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

EMULATOR_WS_URL=%s://%s/ws
MAIL_FROM_ADDRESS=no-reply@%s
`, scheme, cfg.Domain, cfg.DbHost, cfg.DbPort, cfg.DbName, cfg.DbUser, cfg.DbPass,
		cfg.RedisHost, cfg.RedisPort, scheme, cfg.Domain, cfg.Domain)
	return os.WriteFile(filepath.Join(cfg.cmsDir(), ".env"), []byte(env), 0o600)
}

func stepRunMigrations(cfg *Config) error {
	return run(javaBin(), "-jar", cfg.emulatorJar(),
		"--config", cfg.emulatorConfig(), "--migrate-only")
}

func stepCmsKey(cfg *Config) error {
	return run(phpBin(), filepath.Join(cfg.cmsDir(), "artisan"), "key:generate", "--force")
}

// stepSeedData puts the content a hotel needs into the database.
//
// A freshly migrated hotel has no room shapes, no furniture and an empty
// catalogue, so no room can be created, nothing can be put down and there is
// nothing to buy. This used to call a Laravel seeder named InitialSeeder,
// which does not exist and never has, so every install finished with an empty
// hotel.
//
// The seed is applied through the database client rather than through Laravel
// because the content belongs to the hotel, not to the website, and the hotel
// has no PHP in it.
func stepSeedData(cfg *Config) error {
	if err := applySeed(cfg, "base", seed.Base()); err != nil {
		return err
	}
	return run(phpBin(), filepath.Join(cfg.cmsDir(), "artisan"), "config:cache")
}

// applySeed feeds one seed file to the database.
//
// Each statement is sent separately so a failure names the statement that
// failed rather than the whole file.
func applySeed(cfg *Config, name, sql string) error {
	statements := seed.Statements(sql)
	if len(statements) == 0 {
		return fmt.Errorf("the %s seed is empty", name)
	}

	for i, statement := range statements {
		cmd := exec.Command(mysqlBin(),
			"-h", cfg.DbHost, "-u", cfg.DbUser, "-p"+cfg.DbPass, cfg.DbName)
		cmd.Stdin = strings.NewReader(statement)
		cmd.Stderr = os.Stderr

		if err := cmd.Run(); err != nil {
			return fmt.Errorf("%s seed, statement %d of %d: %w\n%s",
				name, i+1, len(statements), err, firstLine(statement))
		}
	}
	return nil
}

// firstLine is enough of a statement to recognise it in an error.
func firstLine(statement string) string {
	if idx := strings.IndexByte(statement, '\n'); idx > 0 {
		return statement[:idx] + " ..."
	}
	return statement
}

// mysqlBin is the client to pipe SQL through. MariaDB renamed it; both names
// are around, so try the current one first and fall back.
func mysqlBin() string {
	if have("mariadb") {
		return "mariadb"
	}
	return "mysql"
}

func stepHealthCheck(_ *Config) error {
	for i := 0; i < 15; i++ {
		if err := httpOK("http://127.0.0.1:8080/health"); err == nil {
			return nil
		}
		time.Sleep(2 * time.Second)
	}
	return fmt.Errorf("emulator did not become healthy on 127.0.0.1:8080 within 30s")
}

func stepWriteState(cfg *Config) error {
	return state.Save(&state.State{
		InstalledVersion: cfg.Version,
		InstallPath:      cfg.InstallPath,
		InstalledAt:      time.Now(),
		Services:         serviceNames(),
		DbMigrationLevel: 9,
	})
}

// ─── helpers ────────────────────────────────────────────────────────────────

func run(name string, args ...string) error {
	cmd := exec.Command(name, args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

// quiet runs a command discarding its output, used for probe commands whose
// failure is expected and handled by the caller.
func quiet(name string, args ...string) error {
	return exec.Command(name, args...).Run()
}

func have(bin string) bool {
	_, err := exec.LookPath(bin)
	return err == nil
}

// httpOK reports whether url answers with a 2xx status.
func httpOK(url string) error {
	client := &http.Client{Timeout: 3 * time.Second}
	resp, err := client.Get(url)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return fmt.Errorf("%s returned HTTP %d", url, resp.StatusCode)
	}
	return nil
}

func javaBin() string {
	if runtime.GOOS == "windows" {
		return "java.exe"
	}
	return "java"
}

func phpBin() string {
	if runtime.GOOS == "windows" {
		return "php.exe"
	}
	if have("php8.3") {
		return "php8.3"
	}
	return "php"
}
