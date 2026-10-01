package main

import (
	"fmt"
	"net/http"
	"os"
	"os/exec"
	"runtime"
	"time"

	"github.com/spf13/cobra"

	"github.com/habnut/launcher/internal/doctor"
	"github.com/habnut/launcher/internal/imager"
	"github.com/habnut/launcher/internal/installer"
	"github.com/habnut/launcher/internal/payload"
	"github.com/habnut/launcher/internal/service"
	"github.com/habnut/launcher/internal/state"
	"github.com/habnut/launcher/internal/swf"
	"github.com/habnut/launcher/internal/updater"
)

// version is overridden at build time via -ldflags "-X main.version=…".
var version = "1.0.0"

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
		cmdImager(),
		cmdDoctor(),
		cmdMigrate(),
		cmdDev(),
		cmdVersion(),
	)

	if err := root.Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}

// cmdInstall runs the installer.
func cmdInstall() *cobra.Command {
	cfg := installer.DefaultConfig()
	cmd := &cobra.Command{
		Use:   "install",
		Short: fmt.Sprintf("Install Habnut on this server (%d-step setup)", len(installer.Steps())),
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

// forEachService applies an action to every managed service, reporting the
// first failure but attempting the remainder so a single missing unit does not
// leave the rest of the hotel in a half-changed state.
func forEachService(action string, fn func(string) error) error {
	var firstErr error
	for _, svc := range service.Names {
		if err := fn(svc); err != nil && firstErr == nil {
			firstErr = fmt.Errorf("%s %s: %w", action, svc, err)
		}
	}
	return firstErr
}

func cmdStart() *cobra.Command {
	return &cobra.Command{Use: "start", Short: "Start all Habnut services", RunE: func(_ *cobra.Command, _ []string) error {
		return forEachService("start", service.Start)
	}}
}

func cmdStop() *cobra.Command {
	return &cobra.Command{Use: "stop", Short: "Stop all Habnut services", RunE: func(_ *cobra.Command, _ []string) error {
		return forEachService("stop", service.Stop)
	}}
}

func cmdRestart() *cobra.Command {
	return &cobra.Command{Use: "restart", Short: "Restart all Habnut services", RunE: func(_ *cobra.Command, _ []string) error {
		return forEachService("restart", service.Restart)
	}}
}

func cmdStatus() *cobra.Command {
	return &cobra.Command{Use: "status", Short: "Show status of all Habnut services", RunE: func(_ *cobra.Command, _ []string) error {
		s, _ := state.Load()
		if s != nil {
			fmt.Printf("Habnut v%s (installed %s)\n\n", s.InstalledVersion, s.InstalledAt.Format("2006-01-02"))
		} else {
			fmt.Println("Habnut is not installed on this machine.")
			fmt.Println()
		}
		for _, st := range service.StatusAll() {
			mark := "✗"
			if st.Active {
				mark = "✓"
			}
			fmt.Printf("  %s %-20s %s\n", mark, st.Name, st.State)
		}
		return nil
	}}
}

func cmdLogs() *cobra.Command {
	var follow bool
	var lines int
	cmd := &cobra.Command{Use: "logs [service]", Short: "Show logs for a service", RunE: func(_ *cobra.Command, args []string) error {
		svc := service.Names[0]
		if len(args) > 0 {
			svc = args[0]
		}
		return service.Logs(svc, lines, follow)
	}}
	cmd.Flags().BoolVarP(&follow, "follow", "f", false, "Follow log output")
	cmd.Flags().IntVarP(&lines, "lines", "n", 100, "Number of lines to show")
	return cmd
}

func cmdRepair() *cobra.Command {
	return &cobra.Command{Use: "repair", Short: "Attempt to repair a broken installation", RunE: func(_ *cobra.Command, _ []string) error {
		fmt.Println("Running repair…")
		_ = forEachService("stop", service.Stop)
		time.Sleep(2 * time.Second)
		return forEachService("start", service.Start)
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

// cmdImager serves avatar and badge pictures from the installed asset pack.
//
// A hotel needs a picture of a figure in far more places than the game client:
// profiles, staff lists, forum posts, the news. Rendering them here means those
// pictures come from the artwork this hotel actually runs, and keeps working
// when nothing outside the server is reachable.
func cmdImager() *cobra.Command {
	var listen, era string

	cmd := &cobra.Command{
		Use:   "imager",
		Short: "Serve avatar and badge images from the installed pack",
		RunE: func(_ *cobra.Command, _ []string) error {
			if !swf.ValidEra(era) {
				return fmt.Errorf("unknown era %q: expected %s or %s",
					era, swf.EraClassic, swf.EraModern)
			}

			server := imager.NewServer(swf.AssetsRoot(), era)
			fmt.Printf("Imager listening on %s, serving the %s era by default\n", listen, era)

			httpServer := &http.Server{
				Addr:              listen,
				Handler:           server.Handler(),
				ReadHeaderTimeout: 10 * time.Second,
			}
			return httpServer.ListenAndServe()
		},
	}

	cmd.Flags().StringVar(&listen, "listen", ":8081", "Address to listen on")
	cmd.Flags().StringVar(&era, "era", swf.EraModern,
		"Era to serve when a request does not name one")
	return cmd
}

// cmdSwf provides SWF asset pack management sub-commands.
//
// Packs are installed per visual era. A hotel may install both, and each player
// then chooses which artwork they see without leaving the room or changing
// world.
func cmdSwf() *cobra.Command {
	cmd := &cobra.Command{Use: "swf", Short: "Manage SWF asset packs"}

	// eraFlag attaches a shared --era flag to a sub-command.
	eraFlag := func(c *cobra.Command, target *string) *cobra.Command {
		c.Flags().StringVar(target, "era", swf.EraModern,
			"Visual era to install into: classic or modern")
		return c
	}

	var installEra, updateEra, rollbackEra string

	cmd.AddCommand(
		eraFlag(&cobra.Command{
			Use:   "install <pack.zip>",
			Short: "Install a SWF pack into one visual era",
			Args:  cobra.ExactArgs(1),
			RunE: func(_ *cobra.Command, args []string) error {
				return swf.Install(args[0], installEra)
			},
		}, &installEra),

		eraFlag(&cobra.Command{
			Use:   "update <pack.zip>",
			Short: "Update the pack installed for one visual era",
			Args:  cobra.ExactArgs(1),
			RunE: func(_ *cobra.Command, args []string) error {
				return swf.Update(args[0], updateEra)
			},
		}, &updateEra),

		&cobra.Command{Use: "rebrand <name>", Short: "Apply branding strings to the asset pack", Args: cobra.ExactArgs(1), RunE: func(_ *cobra.Command, args []string) error {
			return swf.Rebrand(args[0])
		}},
		&cobra.Command{Use: "validate", Short: "Validate that every installed era is complete", RunE: func(_ *cobra.Command, _ []string) error {
			if err := swf.Validate(); err != nil {
				return err
			}
			fmt.Printf("Assets valid. Installed eras: %v\n", swf.InstalledEras())
			return nil
		}},

		eraFlag(&cobra.Command{
			Use:   "rollback <backup.zip>",
			Short: "Restore a previous SWF pack for one visual era",
			Args:  cobra.ExactArgs(1),
			RunE: func(_ *cobra.Command, args []string) error {
				return swf.Rollback(args[0], rollbackEra)
			},
		}, &rollbackEra),

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
		fmt.Print("🌰 Habnut Doctor\n\n")
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
		fmt.Printf("habnutctl v%s (%s/%s)\n", version, runtime.GOOS, runtime.GOARCH)
		if payload.Available() {
			fmt.Printf("Bundled server components: v%s (emulator, client, CMS)\n", payload.Version())
		} else {
			fmt.Println("Bundled server components: none — this is a development build")
		}
		if s, err := state.Load(); err == nil {
			fmt.Printf("Habnut v%s installed at %s\n", s.InstalledVersion, s.InstallPath)
		}
	}}
}
