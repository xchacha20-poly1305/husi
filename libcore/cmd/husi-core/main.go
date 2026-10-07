//go:build !android

package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	SC "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"

	"github.com/spf13/cobra"
	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/daemonhost"
)

// version is the husi release version, stamped at link time with
// -ldflags "-X main.version=…".
var version = "dev"

const sessionSocketName = "api.sock"

type ExitCodeError struct {
	code int
}

func (e *ExitCodeError) Error() string {
	return F.ToString("exit code ", e.code)
}

func main() {
	os.Exit(execute(os.Args[1:]))
}

func execute(args []string) int {
	root := newRootCommand()
	root.SetArgs(args)
	err := root.Execute()
	if err == nil {
		return 0
	}
	if exitCode, isExitCodeErr := errors.AsType[*ExitCodeError](err); isExitCodeErr {
		return exitCode.code
	}
	log.FatalContext(root.Context(), err)
	return 1
}

func newRootCommand() *cobra.Command {
	root := &cobra.Command{
		Use:   "husi-core",
		Short: "Husi desktop core host",
		PersistentPreRun: func(command *cobra.Command, _ []string) {
			setupLogger(command.Context())
		},
		SilenceErrors: true,
		SilenceUsage:  true,
	}
	root.AddCommand(
		newVersionCommand(),
		newRunCommand(),
		newSessionCommand(),
		newServiceCommand(),
	)
	return root
}

func setupLogger(ctx context.Context) {
	factory := log.NewDefaultFactory(
		ctx,
		log.Formatter{
			BaseTime:         time.Now(),
			DisableColors:    true,
			DisableTimestamp: false,
		},
		os.Stderr,
		"",
		nil,
		false,
	)
	common.Must(factory.Start())
	log.SetStdLogger(factory.Logger())
}

func newVersionCommand() *cobra.Command {
	return &cobra.Command{
		Use:   "version",
		Short: "Print version information",
		Args:  cobra.NoArgs,
		Run: func(command *cobra.Command, _ []string) {
			output := command.OutOrStdout()
			fmt.Fprintf(output, "husi-core version %s\n", version)
			fmt.Fprintf(output, "sing-box version %s\n", SC.Version)
			fmt.Fprintf(output, "core api version %d\n", daemon.APIVersion)
			fmt.Fprintf(output, "%s\n", coresvc.BuildEnvironment())
		},
	}
}

func newRunCommand() *cobra.Command {
	var options daemonhost.DaemonHostOptions
	command := &cobra.Command{
		Use:   "run",
		Short: "Run the privileged system daemon",
		Args:  cobra.NoArgs,
		RunE: func(command *cobra.Command, _ []string) error {
			return runDaemon(command.Context(), options)
		},
	}
	flags := command.Flags()
	flags.StringVar(&options.WorkingDir, "dir", "", "working directory (default: platform-specific)")
	flags.StringVar(&options.SocketPath, "socket", "", "gRPC socket or named pipe path (default: platform-specific)")
	flags.StringVar(&options.ListenAddr, "listen", "", "TCP address for dev mode (optional; disables peer auth)")
	flags.StringVar(&options.ConfigPath, "config", "", "daemon config file (default: <dir>/daemon.json)")
	return command
}

func runDaemon(ctx context.Context, options daemonhost.DaemonHostOptions) error {
	if options.WorkingDir == "" {
		options.WorkingDir = daemonhost.DefaultWorkingDir()
	}
	absDir, err := filepath.Abs(options.WorkingDir)
	if err != nil {
		return E.Cause(err, "resolve working directory")
	}
	err = os.MkdirAll(absDir, 0o700)
	if err != nil {
		return E.Cause(err, "create working directory")
	}
	options.WorkingDir = absDir
	options.Version = version

	configPath := options.ConfigPath
	if configPath == "" {
		configPath = daemonhost.DefaultConfigPath(absDir)
	}
	log.InfoContext(ctx, "starting daemon host dir=", absDir, " socket=", options.SocketPath, " listen=", options.ListenAddr, " config=", configPath)

	ctx, stop := signal.NotifyContext(ctx, os.Interrupt, syscall.SIGTERM)
	defer stop()
	return daemonhost.NewDaemonHost(options).Run(ctx)
}

func newSessionCommand() *cobra.Command {
	var options daemonhost.SessionOptions
	command := &cobra.Command{
		Use:   "session",
		Short: "Run the per-user session host",
		Args:  cobra.NoArgs,
		RunE: func(command *cobra.Command, _ []string) error {
			return runSession(command.Context(), options)
		},
	}
	flags := command.Flags()
	flags.StringVar(&options.WorkingDir, "dir", "", "working directory for the session")
	flags.StringVar(&options.SocketPath, "socket", "", "gRPC socket path (default: <dir>/"+sessionSocketName+")")
	common.Must(command.MarkFlagRequired("dir"))
	return command
}

func runSession(ctx context.Context, options daemonhost.SessionOptions) error {
	absDir, err := filepath.Abs(options.WorkingDir)
	if err != nil {
		return E.Cause(err, "resolve working directory")
	}
	options.WorkingDir = absDir
	if options.SocketPath == "" {
		options.SocketPath = filepath.Join(absDir, sessionSocketName)
	} else if !filepath.IsAbs(options.SocketPath) {
		options.SocketPath = filepath.Join(absDir, options.SocketPath)
	}
	options.Version = version

	log.Info("starting session host dir=", absDir, " socket=", options.SocketPath)

	ctx, stop := signal.NotifyContext(ctx, os.Interrupt, syscall.SIGTERM)
	defer stop()
	return daemonhost.NewSessionHost(options).Run(ctx)
}

func newServiceCommand() *cobra.Command {
	command := &cobra.Command{
		Use:   "service",
		Short: "Manage the system daemon service",
		Args:  cobra.NoArgs,
		// Cobra prints help and succeeds for any arguments given to a command
		// that cannot run; being runnable makes Args reject an unknown verb.
		RunE: func(command *cobra.Command, _ []string) error {
			return command.Help()
		},
	}
	command.AddCommand(
		newServiceInstallCommand(),
		newServiceUninstallCommand(),
		&cobra.Command{
			Use:   "start",
			Short: "Start the system daemon service",
			Args:  cobra.NoArgs,
			RunE: func(*cobra.Command, []string) error {
				return daemonhost.ServiceStart()
			},
		},
		&cobra.Command{
			Use:   "stop",
			Short: "Stop the system daemon service",
			Args:  cobra.NoArgs,
			RunE: func(*cobra.Command, []string) error {
				return daemonhost.ServiceStop()
			},
		},
		&cobra.Command{
			Use:   "status",
			Short: "Print the system daemon service state; the exit code reflects it",
			Args:  cobra.NoArgs,
			RunE: func(command *cobra.Command, _ []string) error {
				result, err := daemonhost.ServiceStatus()
				if err != nil {
					return err
				}
				fmt.Fprintln(command.OutOrStdout(), result.Description)
				if result.ExitCode != 0 {
					return &ExitCodeError{code: result.ExitCode}
				}
				return nil
			},
		},
	)
	return command
}

func newServiceInstallCommand() *cobra.Command {
	var workingDir string
	command := &cobra.Command{
		Use:   "install",
		Short: "Install the system daemon service",
		Args:  cobra.NoArgs,
		RunE: func(*cobra.Command, []string) error {
			return daemonhost.ServiceInstall(workingDir)
		},
	}
	command.Flags().StringVar(&workingDir, "dir", "", "daemon working directory (default: platform-specific)")
	return command
}

func newServiceUninstallCommand() *cobra.Command {
	var (
		workingDir string
		purge      bool
	)
	command := &cobra.Command{
		Use:   "uninstall",
		Short: "Uninstall the system daemon service",
		Args:  cobra.NoArgs,
		RunE: func(*cobra.Command, []string) error {
			return daemonhost.ServiceUninstall(workingDir, purge)
		},
	}
	flags := command.Flags()
	flags.StringVar(&workingDir, "dir", "", "daemon working directory (default: platform-specific)")
	flags.BoolVar(&purge, "purge", false, "also remove the working directory")
	return command
}
