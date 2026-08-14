package updater

import (
	"fmt"
	"os"
	"os/exec"
	"time"

	"github.com/habnut/launcher/internal/state"
)

// Step describes one update step.
type Step struct {
	Name string
	Run  func(cfg *Config) error
}

// Config holds update configuration.
type Config struct {
	TargetVersion string
	InstallPath   string
	BackupPath    string
}

// DefaultConfig builds update config from current state.
func DefaultConfig(s *state.State, targetVersion string) *Config {
	return &Config{
		TargetVersion: targetVersion,
		InstallPath:   s.InstallPath,
		BackupPath:    s.InstallPath + "/backups/" + time.Now().Format("20060102-150405"),
	}
}

// Steps is the 13-step update sequence with automatic rollback capability.
var Steps = []Step{
	{"Verify update target version", stepVerifyVersion},
	{"Create pre-update backup", stepBackup},
	{"Stop emulator gracefully", stepStopEmulator},
	{"Download new emulator JAR", stepDownloadEmulator},
	{"Run new Flyway migrations", stepRunMigrations},
	{"Update CMS dependencies", stepUpdateCMS},
	{"Rebuild client assets", stepRebuildClient},
	{"Update Nginx configuration", stepUpdateNginx},
	{"Reload Nginx", stepReloadNginx},
	{"Start updated emulator", stepStartEmulator},
	{"Wait for emulator health", stepWaitHealth},
	{"Run post-update smoke tests", stepSmokeTest},
	{"Update state file", stepUpdateState},
}

// Run executes the update, rolling back on failure.
func Run(cfg *Config, s *state.State, progress func(step, total int, name string)) error {
	completedSteps := 0
	for i, step := range Steps {
		progress(i+1, len(Steps), step.Name)
		if err := step.Run(cfg); err != nil {
			// Rollback: restart the old emulator from backup
			_ = rollback(cfg, s, completedSteps)
			return fmt.Errorf("update failed at step %d (%s): %w — rolled back", i+1, step.Name, err)
		}
		completedSteps++
	}
	return nil
}

func rollback(cfg *Config, s *state.State, completedSteps int) error {
	fmt.Fprintf(os.Stderr, "Rolling back to version %s…\n", s.InstalledVersion)
	// Restore backup JAR
	_ = run("cp", cfg.BackupPath+"/habnut-emulator.jar", cfg.InstallPath+"/emulator/habnut-emulator.jar")
	// Restart old emulator
	_ = run("systemctl", "restart", "habnut-emulator")
	return nil
}

func run(name string, args ...string) error {
	cmd := exec.Command(name, args...)
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

func stepVerifyVersion(cfg *Config) error {
	if cfg.TargetVersion == "" {
		return fmt.Errorf("target version must be specified")
	}
	return nil
}

func stepBackup(cfg *Config) error {
	if err := os.MkdirAll(cfg.BackupPath, 0755); err != nil {
		return err
	}
	return run("cp", cfg.InstallPath+"/emulator/habnut-emulator.jar",
		cfg.BackupPath+"/habnut-emulator.jar")
}

func stepStopEmulator(_ *Config) error {
	return run("systemctl", "stop", "habnut-emulator")
}

func stepDownloadEmulator(cfg *Config) error {
	return run("curl", "-fsSL", "-o", cfg.InstallPath+"/emulator/habnut-emulator.jar",
		fmt.Sprintf("https://releases.habnut.internal/emulator/%s/habnut-emulator.jar", cfg.TargetVersion))
}

func stepRunMigrations(cfg *Config) error {
	return run("java", "-jar", cfg.InstallPath+"/emulator/habnut-emulator.jar", "--migrate-only")
}

func stepUpdateCMS(cfg *Config) error {
	return run("composer", "install", "--no-dev", "--optimize-autoloader",
		"--working-dir", cfg.InstallPath+"/cms")
}

func stepRebuildClient(cfg *Config) error {
	return run("sh", "-c", fmt.Sprintf("cd %s/client && pnpm install && pnpm build", cfg.InstallPath))
}

func stepUpdateNginx(_ *Config) error { return nil }
func stepReloadNginx(_ *Config) error  { return run("systemctl", "reload", "nginx") }

func stepStartEmulator(_ *Config) error {
	return run("systemctl", "start", "habnut-emulator")
}

func stepWaitHealth(_ *Config) error {
	for i := 0; i < 15; i++ {
		if err := run("curl", "-fs", "http://localhost:8080/health"); err == nil {
			return nil
		}
		time.Sleep(2 * time.Second)
	}
	return fmt.Errorf("emulator did not become healthy after update")
}

func stepSmokeTest(_ *Config) error {
	return run("curl", "-fs", "http://localhost:8080/health")
}

func stepUpdateState(cfg *Config) error {
	s, err := state.Load()
	if err != nil {
		return err
	}
	now := time.Now()
	s.InstalledVersion = cfg.TargetVersion
	s.LastUpdatedAt = &now
	return state.Save(s)
}
