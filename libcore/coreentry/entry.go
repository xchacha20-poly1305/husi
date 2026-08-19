//go:build !android

// Package coreentry is the C ABI entry for the husi-core process host.
// It is linked into the desktop anja c-shared library (not Android).
//
// Inclusion: passed via anja bind -linkonly, which blank-imports this package
// from the generated main so HusiCoreMain is linked without bindings and
// without an import cycle (libcore → coreentry → daemonhost → libcore).
package coreentry

/*
#include <stdlib.h>
*/
import "C"

import (
	"context"
	"flag"
	"fmt"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"
	"unsafe"

	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2"
	"github.com/xchacha20-poly1305/husi/libcore/v2/daemonhost"
)

// Name is the canonical process name, used as the argv0 fallback.
const Name = "husi-core"

//export HusiCoreMain
func HusiCoreMain(argc C.int, argv **C.char) C.int {
	args := cArgs(argc, argv)
	return C.int(Main(args))
}

// Main is the Go entry used by tests and HusiCoreMain.
// args[0] is the program name (argv0), matching os.Args.
func Main(args []string) int {
	setupLogger()
	if len(args) < 2 {
		fmt.Fprintln(os.Stderr, "usage: husi-core <run|session|service|version> …")
		return 2
	}
	switch args[1] {
	case "version":
		return cmdVersion()
	case "run":
		return cmdRun(args[2:])
	case "session":
		return cmdSession(args[2:])
	case "service":
		return cmdService(args[2:])
	default:
		fmt.Fprintf(os.Stderr, "unknown command %q\n", args[1])
		fmt.Fprintln(os.Stderr, "usage: husi-core <run|session|service|version> …")
		return 2
	}
}

func setupLogger() {
	log.SetStdLogger(log.NewDefaultFactory(
		context.Background(),
		log.Formatter{
			BaseTime:         time.Now(),
			DisableColors:    true,
			DisableTimestamp: false,
		},
		os.Stderr,
		"",
		nil,
		false,
	).Logger())
}

func cArgs(argc C.int, argv **C.char) []string {
	if argc <= 0 || argv == nil {
		return []string{Name}
	}
	length := int(argc)
	args := unsafe.Slice(argv, length)
	return common.Map(args, func(it *C.char) string {
		return C.GoString(it)
	})
}

func cmdVersion() int {
	fmt.Fprintf(os.Stdout, "husi-core version %s\n", libcore.Version)
	fmt.Fprintf(os.Stdout, "sing-box version %s\n", libcore.VersionBox())
	fmt.Fprintf(os.Stdout, "core api version %d\n", daemon.APIVersion)
	fmt.Fprintf(os.Stdout, "%s\n", libcore.BuildEnvironment())
	return 0
}

func cmdRun(args []string) int {
	fs := flag.NewFlagSet("run", flag.ContinueOnError)
	fs.SetOutput(os.Stderr)
	workingDir := fs.String("dir", "", "working directory (default: platform-specific)")
	socketPath := fs.String("socket", "", "gRPC socket or named pipe path (default: platform-specific)")
	listenAddr := fs.String("listen", "", "TCP address for dev mode (optional; disables peer auth)")
	if err := fs.Parse(args); err != nil {
		return 2
	}
	if fs.NArg() != 0 {
		fmt.Fprintln(os.Stderr, "run: unexpected arguments")
		return 2
	}

	dir := *workingDir
	if dir == "" {
		dir = daemonhost.DefaultWorkingDir()
	}
	absDir, err := filepath.Abs(dir)
	if err != nil {
		log.Error(E.Cause(err, "resolve working directory"))
		return 1
	}
	if err := os.MkdirAll(absDir, 0o700); err != nil {
		log.Error(E.Cause(err, "create working directory"))
		return 1
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	log.Info("starting daemon host dir=", absDir, " socket=", *socketPath, " listen=", *listenAddr)
	host := daemonhost.NewDaemonHost(daemonhost.DaemonHostOptions{
		WorkingDir: absDir,
		SocketPath: *socketPath,
		ListenAddr: *listenAddr,
		Version:    libcore.Version,
	})
	if err := host.Run(ctx); err != nil {
		log.Error(err)
		return 1
	}
	return 0
}

func cmdSession(args []string) int {
	fs := flag.NewFlagSet("session", flag.ContinueOnError)
	fs.SetOutput(os.Stderr)
	workingDir := fs.String("dir", "", "working directory for the session (required)")
	socketPath := fs.String("socket", "", "gRPC socket path (default: <dir>/api.sock)")
	if err := fs.Parse(args); err != nil {
		return 2
	}
	if fs.NArg() != 0 {
		fmt.Fprintln(os.Stderr, "session: unexpected arguments")
		return 2
	}
	if *workingDir == "" {
		fmt.Fprintln(os.Stderr, "session: missing --dir")
		return 2
	}
	absDir, err := filepath.Abs(*workingDir)
	if err != nil {
		log.Error(E.Cause(err, "resolve working directory"))
		return 1
	}
	sock := *socketPath
	if sock == "" {
		sock = filepath.Join(absDir, "api.sock")
	} else if !filepath.IsAbs(sock) {
		sock = filepath.Join(absDir, sock)
	}

	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	log.Info("starting session host dir=", absDir, " socket=", sock)
	host := daemonhost.NewSessionHost(daemonhost.SessionOptions{
		WorkingDir: absDir,
		SocketPath: sock,
		Version:    libcore.Version,
	})
	if err := host.Run(ctx); err != nil {
		log.Error(err)
		return 1
	}
	return 0
}

func cmdService(args []string) int {
	if len(args) < 1 {
		fmt.Fprintln(os.Stderr, "usage: husi-core service <install|uninstall|start|stop|status> …")
		return 2
	}
	// Shared flags may appear before or after the subcommand in historical
	// callers; accept them on a parent set then re-parse remaining for the verb.
	// Practical grammar: service [--dir D] [--purge] <verb>
	//                 or service <verb> [--dir D] [--purge]
	verb, rest, err := splitServiceArgs(args)
	if err != nil {
		fmt.Fprintln(os.Stderr, err)
		return 2
	}
	fs := flag.NewFlagSet("service", flag.ContinueOnError)
	fs.SetOutput(os.Stderr)
	workingDir := fs.String("dir", "", "daemon working directory (default: platform-specific)")
	purge := fs.Bool("purge", false, "on uninstall, also remove the working directory")
	if err := fs.Parse(rest); err != nil {
		return 2
	}
	if fs.NArg() != 0 {
		fmt.Fprintln(os.Stderr, "service: unexpected arguments")
		return 2
	}

	switch verb {
	case "install":
		if err := daemonhost.ServiceInstall(*workingDir); err != nil {
			log.Error(err)
			return 1
		}
		return 0
	case "uninstall":
		if err := daemonhost.ServiceUninstall(*workingDir, *purge); err != nil {
			log.Error(err)
			return 1
		}
		return 0
	case "start":
		if err := daemonhost.ServiceStart(); err != nil {
			log.Error(err)
			return 1
		}
		return 0
	case "stop":
		if err := daemonhost.ServiceStop(); err != nil {
			log.Error(err)
			return 1
		}
		return 0
	case "status":
		result, err := daemonhost.ServiceStatus()
		if err != nil {
			log.Error(err)
			return 1
		}
		fmt.Fprintln(os.Stdout, result.Description)
		return result.ExitCode
	default:
		fmt.Fprintf(os.Stderr, "unknown service command %q\n", verb)
		fmt.Fprintln(os.Stderr, "usage: husi-core service <install|uninstall|start|stop|status> …")
		return 2
	}
}

// splitServiceArgs finds the verb among args that may interleave flags.
// Returns the verb and the remaining args (flags only) for flag.Parse.
func splitServiceArgs(args []string) (verb string, rest []string, err error) {
	verbs := map[string]bool{
		"install": true, "uninstall": true, "start": true, "stop": true, "status": true,
	}
	rest = make([]string, 0, len(args))
	for i := 0; i < len(args); i++ {
		a := args[i]
		if verbs[a] {
			if verb != "" {
				return "", nil, fmt.Errorf("service: multiple verbs")
			}
			verb = a
			continue
		}
		// flag with separate value: --dir VALUE
		if a == "--dir" || a == "-dir" {
			rest = append(rest, a)
			if i+1 < len(args) {
				i++
				rest = append(rest, args[i])
			}
			continue
		}
		rest = append(rest, a)
	}
	if verb == "" {
		return "", nil, fmt.Errorf("usage: husi-core service <install|uninstall|start|stop|status> …")
	}
	return verb, rest, nil
}
