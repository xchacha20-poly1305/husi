package daemonhost

import (
	"context"
	"os"
	"path/filepath"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/distro"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pluginpool"
)

// SessionOptions configures an unprivileged session host (UI child process).
type SessionOptions struct {
	// WorkingDir holds plugin files and defaults the socket path.
	WorkingDir string
	// SocketPath is the UDS path for the gRPC server. Empty means
	// WorkingDir/api.sock.
	SocketPath string
	// Version is the husi version string reported by GetDaemonInfo / GetVersion.
	Version string
	// LogMaxLines is the started-service log ring size. Zero uses Host default.
	LogMaxLines int
	// Watchdog configures stdin/parent-pid liveness detection. Zero values use
	// process defaults.
	Watchdog WatchdogOptions
}

// SessionHost runs coresvc.Host plus session-mode DaemonService and a plugin pool.
type SessionHost struct {
	options SessionOptions
}

// NewSessionHost builds a SessionHost. Call Run to listen and block.
func NewSessionHost(options SessionOptions) *SessionHost {
	return &SessionHost{options: options}
}

// Run starts the gRPC host, plugin pool, and watchdogs, then blocks until ctx
// is cancelled. It always attempts graceful teardown of the host and plugins.
func (h *SessionHost) Run(ctx context.Context) error {
	if ctx == nil {
		return E.New("missing context")
	}
	workingDir := h.options.WorkingDir
	if workingDir == "" {
		return E.New("missing working directory")
	}
	if err := os.MkdirAll(workingDir, 0o700); err != nil {
		return E.Cause(err, "create working directory")
	}
	socketPath := h.options.SocketPath
	if socketPath == "" {
		socketPath = coresvc.SocketPath(workingDir)
	} else if !filepath.IsAbs(socketPath) {
		socketPath = filepath.Join(workingDir, socketPath)
	}

	version := h.options.Version
	if version == "" {
		version = "dev"
	}

	runCtx, cancel := context.WithCancel(ctx)
	defer cancel()

	hostCtx := sessionBaseContext(runCtx)
	holder := coresvc.NewInstanceContextHolder()
	service.MustRegister[*coresvc.InstanceContextHolder](hostCtx, holder)

	daemonSvc := &sessionDaemonService{
		workingDir: workingDir,
		version:    version,
	}
	daemonSvc.plugins = pluginpool.NewPluginPool(workingDir, daemonSvc.handlePluginFatal)

	hostOpts := coresvc.HostOptions{
		Context:     hostCtx,
		Version:     version,
		LogMaxLines: h.options.LogMaxLines,
		Backend: &pooledBackend{
			workingDir: workingDir,
		},
		ExtraServices: daemonSvc,
	}
	host, err := coresvc.NewHost(hostOpts)
	if err != nil {
		_ = daemonSvc.plugins.Close()
		return E.Cause(err, "create host")
	}
	daemonSvc.host = host

	if err := host.Start(socketPath); err != nil {
		_ = host.Close()
		_ = daemonSvc.plugins.Close()
		return E.Cause(err, "start host")
	}
	log.Info("session host listening on ", socketPath)

	StartWatchdog(cancel, h.options.Watchdog)

	<-runCtx.Done()
	log.Info("session host shutting down")

	daemonSvc.access.Lock()
	_ = daemonSvc.stopLocked()
	daemonSvc.access.Unlock()

	return host.Close()
}

func sessionBaseContext(parent context.Context) context.Context {
	if parent == nil {
		parent = context.Background()
	}
	return box.Context(
		parent,
		distro.InboundRegistry(),
		distro.OutboundRegistry(),
		distro.EndpointRegistry(),
		distro.DNSTransportRegistry(),
		distro.ServiceRegistry(),
		distro.CertificateProviderRegistry(),
	)
}
