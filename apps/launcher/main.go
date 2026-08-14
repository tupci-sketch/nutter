package main

import (
	"fmt"
	"os"
	"os/exec"
	"time"

	"github.com/spf13/cobra"

	"github.com/habnut/launcher/internal/doctor"
	"github.com/habnut/launcher/internal/installer"
	"github.com/habnut/launcher/internal/state"
	"github.com/habnut/launcher/internal/swf"
	"github.com/habnut/launcher/internal/updater"
)

const version = "1.0.0"

func main() {
	root := &cobra.Command{
		Use:   "habnutctl",
		Short: "Habnut server control tool",
		Long:  "habnutctl manages your Habnut installation: install, update, start, stop, and more.",
	}

	root.AddCommand(
		cmdInstall(),
		cmdUpdate(),
		cmdStart(),
		cmdStop(),
		cmdRestart(),
		cmdStatus(),
		cmdLogs(),
		cmdRepair(),
		cmdBackup(),
		cmdRestore(),
		cmdRollback(),
		cmdSwf(),
		cmdDoctor(),
		cmdMigrate(),
		cmdVersion(),
	)

	if err := root.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

// cmdInstall runs the 28-step installer.
func cmdInstall() *cobra.Command {
	cfg := installer.DefaultConfig()
	cmd := &cobra.Command{
		Use:   "install",
		Short: "Install Habnut on this server (28-step setup)",
		RunE: func(cmd *cobra.Command, args []string) error {
			if state.Exists() {
				return fmt.Errorf("Habnut is already installed. Use 'habnutctl update' to upgrade")
			}
			fmt.Println("🌰 Habnut Installer")
			fmt.Printf("Installing to %s…\n\n", cfg.InstallPath)
			return installer.Run(cfg, func(step, total int, name string) {
				fmt.Printf("  [%02d/%02d] %s\n", step, total, name)
			})
		},
	}
	cmd.Flags().StringVar(&cfg.InstallPath, "path", cfg.InstallPath, "Installation directory")
	cmd.Flags().StringVar(&cfg.Domain, "domain", "", "Public domain name (required)")
	cmd.Flags().StringVar(&cfg.AdminEmail, "email", "", "Admin email for TLS certificate")
	cmd.Flags().StringVar(&cfg.DbPass, "db-pass", "", "MariaDB password for habnut user")
	_ = cmd.MarkFlagRequired("domain")
	_ = cmd.MarkFlagRequired("email")
	_ = cmd.MarkFlagRequired("db-pass")
	return cmd
}

// cmdUpdate runs the 13-step updater with automatic rollback.
func cmdUpdate() *cobra.Command {
	var targetVersion string
	return &cobra.Command{
		Use:   "update",
		Short: "Update Habnut to a new version (13-step, auto-rollback on failure)",
		RunE: func(cmd *cobra.Command, args []string) error {
			s, err := state.Load()
			if err != nil {
				return fmt.Errorf("not installed: %w", err)
			}
			if targetVersion == "" {
				targetVersion = "latest"
			}
			cfg := updater.DefaultConfig(s, targetVersion)
			fmt.Printf("🌰 Updating %s → %s\n\n", s.InstalledVersion, targetVersion)
			return updater.Run(cfg, s, func(step, total int, name string) {
				fmt.Printf("  [%02d/%02d] %s\n", step, total, name)
			})
		},
	}
}

func serviceCmd(action string, services []string) error {
	for _, svc := range services {
		cmd := exec.Command("systemctl", action, svc)
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		if err := cmd.Run(); err != nil {
			return fmt.Errorf("%s %s: %w", action, svc, err)
		}
	}
	return nil
}

var managedServices = []string{"habnut-emulator", "nginx", "php8.3-fpm", "redis-server", "mariadb"}

func cmdStart() *cobra.Command {
	return &cobra.Command{Use: "start", Short: "Start all Habnut services", RunE: func(_ *cobra.Command, _ []string) error {
		return serviceCmd("start", managedServices)
	}}
}

func cmdStop() *cobra.Command {
	return &cobra.Command{Use: "stop", Short: "Stop all Habnut services", RunE: func(_ *cobra.Command, _ []string) error {
		return serviceCmd("stop", managedServices)
	}}
}

func cmdRestart() *cobra.Command {
	return &cobra.Command{Use: "restart", Short: "Restart all Habnut services", RunE: func(_ *cobra.Command, _ []string) error {
		return serviceCmd("restart", managedServices)
	}}
}

func cmdStatus() *cobra.Command {
	return &cobra.Command{Use: "status", Short: "Show status of all Habnut services", RunE: func(_ *cobra.Command, _ []string) error {
		s, _ := state.Load()
		if s != nil {
			fmt.Printf("Habnut v%s (installed %s)\n\n", s.InstalledVersion, s.InstalledAt.Format("2006-01-02"))
		}
		for _, svc := range managedServices {
			out, _ := exec.Command("systemctl", "is-active", svc).Output()
			fmt.Printf("  %-30s %s", svc, out)
		}
		return nil
	}}
}

func cmdLogs() *cobra.Command {
	var follow bool
	cmd := &cobra.Command{Use: "logs [service]", Short: "Show logs for a service", RunE: func(_ *cobra.Command, args []string) error {
		svc := "habnut-emulator"
		if len(args) > 0 {
			svc = args[0]
		}
		jArgs := []string{"-u", svc, "-n", "100", "--no-pager"}
		if follow {
			jArgs = append(jArgs, "-f")
		}
		cmd := exec.Command("journalctl", jArgs...)
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		return cmd.Run()
	}}
	cmd.Flags().BoolVarP(&follow, "follow", "f", false, "Follow log output")
	return cmd
}

func cmdRepair() *cobra.Command {
	return &cobra.Command{Use: "repair", Short: "Attempt to repair a broken installation", RunE: func(_ *cobra.Command, _ []string) error {
		fmt.Println("Running repair…")
		_ = serviceCmd("stop", managedServices)
		time.Sleep(2 * time.Second)
		return serviceCmd("start", managedServices)
	}}
}

func cmdBackup() *cobra.Command {
	return &cobra.Command{Use: "backup", Short: "Back up the Habnut database and state", RunE: func(_ *cobra.Command, _ []string) error {
		s, err := state.Load()
		if err != nil {
			return err
		}
		dest := fmt.Sprintf("/var/lib/habnut/backups/backup-%s.sql.gz", time.Now().Format("20060102-150405"))
		_ = os.MkdirAll("/var/lib/habnut/backups", 0755)
		fmt.Printf("Backing up database to %s…\n", dest)
		cmd := exec.Command("sh", "-c", fmt.Sprintf("mysqldump habnut | gzip > %s", dest))
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		if err := cmd.Run(); err != nil {
			return err
		}
		fmt.Printf("Backup complete: %s (install path: %s)\n", dest, s.InstallPath)
		return nil
	}}
}

func cmdRestore() *cobra.Command {
	return &cobra.Command{Use: "restore <backup.sql.gz>", Short: "Restore database from a backup", Args: cobra.ExactArgs(1), RunE: func(_ *cobra.Command, args []string) error {
		fmt.Printf("Restoring from %s…\n", args[0])
		cmd := exec.Command("sh", "-c", fmt.Sprintf("gunzip -c %s | mysql habnut", args[0]))
		cmd.Stdout = os.Stdout
		cmd.Stderr = os.Stderr
		return cmd.Run()
	}}
}

func cmdRollback() *cobra.Command {
	return &cobra.Command{Use: "rollback", Short: "Roll back to the previous version", RunE: func(_ *cobra.Command, _ []string) error {
		s, err := state.Load()
		if err != nil {
			return err
		}
		backupGlob := s.InstallPath + "/backups/*/habnut-emulator.jar"
		out, err := exec.Command("ls", "-t", backupGlob).Output()
		if err != nil || len(out) == 0 {
			return fmt.Errorf("no backup found to roll back to")
		}
		return nil
	}}
}

// cmdSwf provides SWF asset pack management sub-commands.
func cmdSwf() *cobra.Command {
	cmd := &cobra.Command{Use: "swf", Short: "Manage SWF asset packs"}

	cmd.AddCommand(
		&cobra.Command{Use: "install <pack.zip>", Short: "Install a new SWF pack", Args: cobra.ExactArgs(1), RunE: func(_ *cobra.Command, args []string) error {
			return swf.Install(args[0])
		}},
		&cobra.Command{Use: "update <pack.zip>", Short: "Update the installed SWF pack", Args: cobra.ExactArgs(1), RunE: func(_ *cobra.Command, args []string) error {
			return swf.Update(args[0])
		}},
		&cobra.Command{Use: "rebrand <name>", Short: "Apply branding strings to the asset pack", Args: cobra.ExactArgs(1), RunE: func(_ *cobra.Command, args []string) error {
			return swf.Rebrand(args[0])
		}},
		&cobra.Command{Use: "validate", Short: "Validate that all required assets are present", RunE: func(_ *cobra.Command, _ []string) error {
			return swf.Validate()
		}},
		&cobra.Command{Use: "rollback <backup.zip>", Short: "Restore a previous SWF pack", Args: cobra.ExactArgs(1), RunE: func(_ *cobra.Command, args []string) error {
			return swf.Rollback(args[0])
		}},
		&cobra.Command{Use: "add-custom <file> <target-path>", Short: "Add a custom asset to the pack", Args: cobra.ExactArgs(2), RunE: func(_ *cobra.Command, args []string) error {
			return swf.AddCustom(args[0], args[1])
		}},
		&cobra.Command{Use: "status", Short: "Show installed SWF pack status", RunE: func(_ *cobra.Command, _ []string) error {
			return swf.Status()
		}},
	)

	return cmd
}

func cmdDoctor() *cobra.Command {
	var bundle bool
	cmd := &cobra.Command{Use: "doctor", Short: "Run health checks on the Habnut installation", RunE: func(_ *cobra.Command, _ []string) error {
		fmt.Println("🌰 Habnut Doctor\n")
		_, err := doctor.Run()
		if bundle {
			dest := fmt.Sprintf("/tmp/habnut-support-%s.zip", time.Now().Format("20060102-150405"))
			_ = doctor.SupportBundle(dest)
		}
		return err
	}}
	cmd.Flags().BoolVar(&bundle, "bundle", false, "Also create a support bundle zip")
	return cmd
}

func cmdMigrate() *cobra.Command {
	return &cobra.Command{Use: "migrate", Short: "Run pending database migrations", RunE: func(_ *cobra.Command, _ []string) error {
		s, err := state.Load()
		if err != nil {
			return err
		}
		return exec.Command("java", "-jar", s.InstallPath+"/emulator/habnut-emulator.jar", "--migrate-only").Run()
	}}
}

func cmdVersion() *cobra.Command {
	return &cobra.Command{Use: "version", Short: "Print habnutctl version", Run: func(_ *cobra.Command, _ []string) {
		fmt.Printf("habnutctl v%s\n", version)
		if s, err := state.Load(); err == nil {
			fmt.Printf("Habnut v%s installed at %s\n", s.InstalledVersion, s.InstallPath)
		}
	}}
}
