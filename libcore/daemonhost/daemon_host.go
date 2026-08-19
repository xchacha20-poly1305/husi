package daemonhost

import (
	"context"
	"net"
	"os"
	"path/filepath"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2"
	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/distro"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pluginpool"
	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/health/grpc_health_v1"
)

type DaemonHostOptions struct {
	// WorkingDir is root-owned state (snapshots, owner).
	WorkingDir string
	// SocketPath is the UDS or named-pipe path. Empty uses the platform default.
	SocketPath string
	// ListenAddr is an optional TCP address for development only.
	ListenAddr string
	// Version is reported by GetDaemonInfo / GetVersion.
	Version string
	// LogMaxLines is the started-service log ring size. Zero uses Host default.
	LogMaxLines int
}

type DaemonHost struct {
	options DaemonHostOptions
}

func NewDaemonHost(options DaemonHostOptions) *DaemonHost {
	return &DaemonHost{options: options}
}

func DefaultWorkingDir() string {
	return defaultWorkingDir()
}

func DefaultSocketPath() string {
	return defaultSocketPath()
}

func (h *DaemonHost) Run(ctx context.Context) error {
	if ctx == nil {
		return E.New("missing context")
	}
	workingDir := h.options.WorkingDir
	if workingDir == "" {
		workingDir = DefaultWorkingDir()
	}
	absDir, err := filepath.Abs(workingDir)
	if err != nil {
		return E.Cause(err, "resolve working directory")
	}
	if err := os.MkdirAll(absDir, 0o700); err != nil {
		return E.Cause(err, "create working directory")
	}
	coreDir, err := prepareCoreDirs(absDir)
	if err != nil {
		return err
	}
	if err := os.Chdir(coreDir); err != nil {
		return E.Cause(err, "chdir into core directory")
	}

	version := h.options.Version
	if version == "" {
		version = "dev"
	}

	runCtx, cancel := context.WithCancel(ctx)
	defer cancel()

	hostCtx := daemonBaseContext(runCtx)
	holder := coresvc.NewInstanceContextHolder()
	service.MustRegister[*coresvc.InstanceContextHolder](hostCtx, holder)

	registry := NewPeerRegistry()
	owner := NewOwnerStore(registry)
	if saved, err := LoadOwnerState(absDir); err != nil {
		log.Warn("load owner state: ", err)
	} else if saved != nil {
		owner.SetOwner(*saved)
	}

	daemonSvc := newDaemonDaemonService(absDir, version, owner)
	listenTCP := h.options.ListenAddr != ""
	allowAll := listenTCP

	if err := verifyOwnCorePairSignature(); err != nil {
		return err
	}

	platformCreds, err := PlatformServerCredentials(registry, listenTCP)
	if err != nil {
		return E.Cause(err, "platform server credentials")
	}
	authorizer := NewOwnerAuthorizer(owner, allowAll)

	serverOptions := []grpc.ServerOption{
		// Locale first (outermost), then auth — matches the Phase 3 plan.
		grpc.ChainUnaryInterceptor(
			daemon.UnaryLocaleInterceptor,
			UnaryAuthorizeInterceptor(authorizer),
		),
		grpc.ChainStreamInterceptor(
			daemon.StreamLocaleInterceptor,
			StreamAuthorizeInterceptor(authorizer),
		),
	}
	serverOptions = append(serverOptions, platformCreds...)

	hostOpts := coresvc.HostOptions{
		Context:       hostCtx,
		Version:       version,
		LogMaxLines:   h.options.LogMaxLines,
		ServerOptions: serverOptions,
		// Skip default locale chain; we already installed locale+auth above.
		SkipDefaultInterceptors: true,
		CheckConfig:             libcore.CheckConfig,
		GenerateSchema: func(kind husiv1.SchemaKind) (string, error) {
			switch kind {
			case husiv1.SchemaKind_SCHEMA_KIND_CONFIG:
				return libcore.GenerateConfigSchema()
			case husiv1.SchemaKind_SCHEMA_KIND_OUTBOUND:
				return libcore.GenerateOutboundSchema()
			case husiv1.SchemaKind_SCHEMA_KIND_DNS_RULE:
				return libcore.GenerateDNSRuleSchema()
			default:
				return "", E.New("unknown schema kind: ", kind.String())
			}
		},
		StandaloneURLTest: func(config, tag, link string, timeoutMs int32, options uint8, plugins []*husiv1.PluginProcessSpec) (int32, error) {
			// Same owner credential drop as StartService: plugins never run as
			// the privileged daemon.
			return pluginpool.RunWithPlugins(absDir, plugins, daemonSvc.pluginCredentials, func() (int32, error) {
				return libcore.StandaloneURLTest(config, tag, link, timeoutMs, options, nil)
			})
		},
		BuildEnvironment: libcore.BuildEnvironment,
		RegisterExtra: func(server *grpc.Server, healthServer *health.Server) {
			husiv1.RegisterDaemonServiceServer(server, daemonSvc)
			healthServer.SetServingStatus(husiv1.DaemonService_ServiceDesc.ServiceName, grpc_health_v1.HealthCheckResponse_SERVING)
		},
	}
	libcore.WireApplicationTools(&hostOpts)
	host, err := coresvc.NewHost(hostOpts)
	if err != nil {
		_ = daemonSvc.plugins.Close()
		return E.Cause(err, "create host")
	}
	daemonSvc.host = host

	listener, err := h.listen()
	if err != nil {
		_ = host.Close()
		_ = daemonSvc.plugins.Close()
		return E.Cause(err, "listen")
	}

	if err := host.StartOn(listener); err != nil {
		_ = listener.Close()
		_ = host.Close()
		_ = daemonSvc.plugins.Close()
		return E.Cause(err, "start host")
	}
	log.Info("daemon host listening on ", listener.Addr())

	go h.attemptBootRestore(runCtx, daemonSvc)

	<-runCtx.Done()
	log.Info("daemon host shutting down")

	daemonSvc.access.Lock()
	_ = daemonSvc.stopLocked(false)
	daemonSvc.access.Unlock()

	return host.Close()
}

func verifyOwnCorePairSignature() error {
	executablePath, err := os.Executable()
	if err != nil {
		return E.Cause(err, "get executable path")
	}
	shimPath, err := resolveExecutablePath(executablePath)
	if err != nil {
		return err
	}
	if err := VerifyCorePairSignature(shimPath); err != nil {
		return E.Cause(err, "verify core pair")
	}
	return nil
}

func (h *DaemonHost) listen() (net.Listener, error) {
	if h.options.ListenAddr != "" {
		log.Warn("listening on TCP address ", h.options.ListenAddr, ": development only, no access control")
		return net.Listen(N.NetworkTCP, h.options.ListenAddr)
	}
	socketPath := h.options.SocketPath
	if socketPath == "" {
		socketPath = DefaultSocketPath()
	}
	return ListenDaemonEndpoint(socketPath)
}

func (h *DaemonHost) attemptBootRestore(ctx context.Context, svc *daemonDaemonService) {
	if !StartAtBoot(svc.workingDir) {
		return
	}
	if !WasRunning(svc.workingDir) {
		return
	}
	snapshot, err := LoadSnapshot(svc.workingDir)
	if err != nil {
		log.Warn("load snapshot for boot restore: ", err)
		return
	}
	if snapshot == nil {
		log.Warn("was_running set but no snapshot found")
		return
	}
	log.Info("restoring service from snapshot")
	if err := svc.restore(ctx, snapshot); err != nil {
		log.Error("boot restore failed: ", err)
	}
}

func daemonBaseContext(parent context.Context) context.Context {
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

// prepareCoreDirs creates the directories a hosted core expects around the
// daemon working directory and returns the directory the core must run in.
// Pushed configs reference paths relative to that directory (for example
// "../cache/cache.db"), mirroring session mode where the UI starts the
// process inside <dataDir>/core.
func prepareCoreDirs(workingDir string) (string, error) {
	coreDir := filepath.Join(workingDir, "core")
	cacheDir := filepath.Join(workingDir, "cache")
	if err := os.MkdirAll(coreDir, 0o700); err != nil {
		return "", E.Cause(err, "create core directory")
	}
	if err := os.MkdirAll(cacheDir, 0o700); err != nil {
		return "", E.Cause(err, "create cache directory")
	}
	return coreDir, nil
}
