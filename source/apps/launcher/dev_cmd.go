package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"

	"github.com/spf13/cobra"

	"github.com/habnut/launcher/internal/dev"
)

// cmdDev runs a whole hotel on this machine, for looking at.
//
// `habnutctl install` is for a server: root, a domain, a certificate, system
// packages, services at boot. This is the other thing somebody wants — to see
// whether it works, on their own computer, before committing a server to it —
// and none of that applies.
func cmdDev() *cobra.Command {
	cmd := &cobra.Command{
		Use:   "dev",
		Short: "Run a whole hotel on this computer",
		Long: "Runs a complete hotel on 127.0.0.1: the website, the game, the database and\n" +
			"the staff pages, already filled with rooms, furniture and people to sign in as.\n" +
			"\n" +
			"Nothing is installed system-wide, nothing needs root, nothing listens anywhere\n" +
			"but this machine, and `habnutctl dev down --purge` removes every trace of it.\n" +
			"\n" +
			"Docker is the only thing it needs that is not inside this binary.",
	}

	cmd.AddCommand(
		cmdDevUp(),
		cmdDevDown(),
		cmdDevStatus(),
		cmdDevLogs(),
		cmdDevReset(),
		cmdDevSeed(),
		cmdDevShell(),
		cmdDevConfig(),
	)

	return cmd
}

// devFlags are the settings every dev subcommand understands.
type devFlags struct {
	root string
	name string
	port int
}

func (f *devFlags) attach(cmd *cobra.Command) {
	cmd.Flags().StringVar(&f.root, "dir", "",
		"where this hotel's files live (default: a Habnut folder in your home directory)")
	cmd.Flags().StringVar(&f.name, "name", "habnut-dev",
		"a name for this hotel, so you can run more than one")
	cmd.Flags().IntVar(&f.port, "port", dev.DefaultHTTPPort,
		"the port to serve the hotel on")
}

// environment builds the hotel these flags describe.
func (f *devFlags) environment() (*dev.Env, error) {
	root := f.root
	if root == "" {
		var err error
		root, err = dev.DefaultRoot()
		if err != nil {
			return nil, err
		}
		// A second hotel gets its own directory beside the first, rather than
		// quietly adopting its database.
		if f.name != "" && f.name != "habnut-dev" {
			root = root + "-" + f.name
		}
	}

	env := dev.New(root, f.name)
	if f.port > 0 {
		env.HTTPPort = f.port
	}
	return env, nil
}

// session connects to Docker and returns everything a dev command needs.
//
// Docker is checked up front rather than at the first command that needs it,
// so somebody who has not installed it is told once, clearly, before anything
// has been half-done.
func (f *devFlags) session(ctx context.Context) (*dev.Session, error) {
	env, err := f.environment()
	if err != nil {
		return nil, err
	}

	docker, err := dev.NewDocker(ctx, env)
	if err != nil {
		if errors.Is(err, dev.ErrNoDocker) {
			return nil, fmt.Errorf("%w\n\n%s", err, dev.DockerAdvice())
		}
		return nil, err
	}

	return &dev.Session{Env: env, Docker: docker, Out: os.Stdout}, nil
}

// interruptible returns a context cancelled by Ctrl-C, so a long first run can
// be stopped without leaving containers half-started.
func interruptible() (context.Context, context.CancelFunc) {
	return signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
}

func cmdDevUp() *cobra.Command {
	var f devFlags
	var noDemo, recreate, rp bool

	cmd := &cobra.Command{
		Use:   "up",
		Short: "Start the hotel on this computer",
		Long: "Brings up a complete hotel on 127.0.0.1 and prints where to find it.\n" +
			"\n" +
			"The first run fetches container images and builds the database, which takes a\n" +
			"few minutes. After that it is seconds.",
		RunE: func(cmd *cobra.Command, _ []string) error {
			ctx, cancel := interruptible()
			defer cancel()

			s, err := f.session(ctx)
			if err != nil {
				return err
			}
			s.Env.Demo = !noDemo
			s.Env.RP = rp
			s.Recreate = recreate

			fmt.Printf("Starting a hotel in %s\n\n", s.Env.Root)

			if err := dev.Up(ctx, s); err != nil {
				return err
			}

			fmt.Println()
			fmt.Println("The hotel is up.")
			fmt.Println()
			dev.PrintWhereToGo(os.Stdout, s.Env)
			dev.PrintAssetNote(os.Stdout, s.Env)
			fmt.Println()
			fmt.Println("  Stop it with:   habnutctl dev down")
			fmt.Println("  Watch it with:  habnutctl dev logs -f")

			return nil
		},
	}

	f.attach(cmd)
	cmd.Flags().BoolVar(&noDemo, "no-demo", false,
		"come up empty, with no accounts, rooms or furniture")
	cmd.Flags().BoolVar(&recreate, "recreate", false,
		"rewrite the generated files, discarding any changes you made to them")
	cmd.Flags().BoolVar(&rp, "rp", false,
		"run the roleplay city instead of the hotel")

	return cmd
}

func cmdDevDown() *cobra.Command {
	var f devFlags
	var purge bool

	cmd := &cobra.Command{
		Use:   "down",
		Short: "Stop the hotel",
		Long: "Stops the hotel. Its database is kept, so `habnutctl dev up` picks up exactly\n" +
			"where it left off.\n" +
			"\n" +
			"With --purge the database goes too, and the next start is a fresh hotel.",
		RunE: func(cmd *cobra.Command, _ []string) error {
			ctx, cancel := interruptible()
			defer cancel()

			s, err := f.session(ctx)
			if err != nil {
				return err
			}
			return dev.Down(ctx, s, purge)
		},
	}

	f.attach(cmd)
	cmd.Flags().BoolVar(&purge, "purge", false, "delete the database as well")

	return cmd
}

func cmdDevStatus() *cobra.Command {
	var f devFlags

	cmd := &cobra.Command{
		Use:   "status",
		Short: "Show whether the hotel is running, and where it is",
		RunE: func(cmd *cobra.Command, _ []string) error {
			ctx, cancel := interruptible()
			defer cancel()

			s, err := f.session(ctx)
			if err != nil {
				return err
			}
			return dev.Status(ctx, s)
		},
	}

	f.attach(cmd)
	return cmd
}

func cmdDevLogs() *cobra.Command {
	var f devFlags
	var follow bool
	var tail int

	cmd := &cobra.Command{
		Use:   "logs [service]",
		Short: "Show what the hotel is saying",
		Long: "Shows the output of every service, or of one of them.\n" +
			"\n" +
			"The services are: db, redis, mail, emulator, cms, web.",
		Args: cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			ctx, cancel := interruptible()
			defer cancel()

			s, err := f.session(ctx)
			if err != nil {
				return err
			}

			service := ""
			if len(args) == 1 {
				service = args[0]
			}
			return s.Docker.Logs(ctx, os.Stdout, service, follow, tail)
		},
	}

	f.attach(cmd)
	cmd.Flags().BoolVarP(&follow, "follow", "f", false, "keep printing as more arrives")
	cmd.Flags().IntVar(&tail, "tail", 200, "how many lines of history to show")

	return cmd
}

func cmdDevReset() *cobra.Command {
	var f devFlags
	var noDemo, yes bool

	cmd := &cobra.Command{
		Use:   "reset",
		Short: "Throw this hotel away and build it again",
		Long: "Deletes the database and everything in it, then builds the hotel again from\n" +
			"nothing. The point of a hotel on your own machine is being able to break it,\n" +
			"so getting back to a known state is one command.",
		RunE: func(cmd *cobra.Command, _ []string) error {
			ctx, cancel := interruptible()
			defer cancel()

			s, err := f.session(ctx)
			if err != nil {
				return err
			}
			s.Env.Demo = !noDemo

			if !yes {
				fmt.Printf("This deletes the database at %s and everything in it.\n", s.Env.Root)
				fmt.Print("Type yes to go ahead: ")
				var answer string
				fmt.Scanln(&answer)
				if answer != "yes" {
					fmt.Println("Left alone.")
					return nil
				}
			}

			if err := dev.Reset(ctx, s); err != nil {
				return err
			}

			fmt.Println()
			fmt.Println("The hotel is up again, as new.")
			fmt.Println()
			dev.PrintWhereToGo(os.Stdout, s.Env)
			return nil
		},
	}

	f.attach(cmd)
	cmd.Flags().BoolVar(&noDemo, "no-demo", false, "come up empty")
	cmd.Flags().BoolVarP(&yes, "yes", "y", false, "do not ask first")

	return cmd
}

func cmdDevSeed() *cobra.Command {
	var f devFlags
	var noDemo bool

	cmd := &cobra.Command{
		Use:   "seed",
		Short: "Add the rooms, furniture and people again",
		Long: "Applies the seed files to a hotel that is already running.\n" +
			"\n" +
			"The files are in the hotel's own directory, under seed/, so you can change\n" +
			"them and run this to apply what you changed. Nothing that is already there is\n" +
			"overwritten, so a hotel you have been playing with keeps what you did to it.",
		RunE: func(cmd *cobra.Command, _ []string) error {
			ctx, cancel := interruptible()
			defer cancel()

			s, err := f.session(ctx)
			if err != nil {
				return err
			}
			return dev.Seed(ctx, s, !noDemo)
		},
	}

	f.attach(cmd)
	cmd.Flags().BoolVar(&noDemo, "no-demo", false, "only the rooms and furniture, no accounts")

	return cmd
}

func cmdDevShell() *cobra.Command {
	var f devFlags

	cmd := &cobra.Command{
		Use:   "db",
		Short: "Open a database prompt on the hotel",
		Long: "Opens a MariaDB prompt on the running hotel, so you can look at what the\n" +
			"hotel is actually storing without installing a client.",
		RunE: func(cmd *cobra.Command, _ []string) error {
			ctx, cancel := interruptible()
			defer cancel()

			s, err := f.session(ctx)
			if err != nil {
				return err
			}

			return s.Docker.Exec(ctx, os.Stdout, "db",
				"mariadb", "-uhabnut", "-phabnut", "habnut")
		},
	}

	f.attach(cmd)
	return cmd
}

// cmdDevConfig writes the generated files without starting anything.
//
// Useful for reading what `dev up` would run before running it, for editing it
// first, and for checking the generated web server config without needing
// Docker — which is how the build checks it.
func cmdDevConfig() *cobra.Command {
	var f devFlags
	var noDemo, rp bool

	cmd := &cobra.Command{
		Use:   "config",
		Short: "Write the stack files without starting anything",
		Long: "Writes the compose file, the web server config and the website's settings\n" +
			"into the hotel's directory, and stops there.\n" +
			"\n" +
			"Needs no Docker. Read them, change them, then `habnutctl dev up` — which\n" +
			"leaves your changes alone unless you pass --recreate.",
		RunE: func(cmd *cobra.Command, _ []string) error {
			env, err := f.environment()
			if err != nil {
				return err
			}
			env.Demo = !noDemo
			env.RP = rp

			if err := env.Create(); err != nil {
				return err
			}

			key, err := dev.AppKey(env)
			if err != nil {
				return err
			}
			if err := env.WriteStack(key, true); err != nil {
				return err
			}

			fmt.Printf("Written to %s:\n", env.Root)
			for _, path := range []string{
				env.ComposeFile(),
				env.NginxConf(),
				filepath.Join(env.CMSDir(), ".env"),
				filepath.Join(env.CMSDir(), "dev-entrypoint.sh"),
			} {
				fmt.Printf("  %s\n", path)
			}
			return nil
		},
	}

	f.attach(cmd)
	cmd.Flags().BoolVar(&noDemo, "no-demo", false, "write settings for a hotel with nobody in it")
	cmd.Flags().BoolVar(&rp, "rp", false, "write settings for the roleplay city")

	return cmd
}
