package coresvc

import (
	"cmp"
	"context"
	"errors"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"syscall"
	"time"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/reflection"
)

type Host struct {
	access sync.Mutex

	ctx              context.Context
	version          string
	buildEnvironment string
	logMaxLines      int
	appHandler       AppHandler

	started *daemon.StartedService
	events  *eventBroadcaster

	services      []ServiceRegistrar
	fileLogSink   log.PlatformWriter
	serverOptions []grpc.ServerOption
	// skipDefaultInterceptors omits the built-in locale interceptors when the
	// caller already chains them with auth in ServerOptions.
	skipDefaultInterceptors bool

	server   *grpc.Server
	listener net.Listener

	stuck   atomic.Bool
	onStuck func(error)
}

type HostOptions struct {
	Context          context.Context
	Version          string
	BuildEnvironment string
	LogMaxLines      int
	AppHandler       AppHandler

	// FileLogSink is attached after a successful StartOrReloadService so box
	// logs also land in stderr.log. Optional.
	FileLogSink log.PlatformWriter
	// Services contribute the gRPC surfaces the Host does not implement itself.
	Services      []ServiceRegistrar
	ServerOptions []grpc.ServerOption
	// SkipDefaultInterceptors skips the built-in locale interceptors. Use when
	// ServerOptions already chain locale + auth in the desired order.
	SkipDefaultInterceptors bool
	OnStuck                 func(error)
}

var (
	ErrCloseTimeout = E.New("sing-box did not close in time")
	ErrHostStuck    = E.New("core host is stuck: sing-box never finished closing")
)

// NewHost constructs a Host. Call Start to listen; Close to tear down.
func NewHost(options HostOptions) (*Host, error) {
	if options.Context == nil {
		return nil, E.New("missing context")
	}
	logMaxLines := max(options.LogMaxLines, 50)
	started := daemon.NewStartedService(daemon.ServiceOptions{
		Context:     options.Context,
		Handler:     platformHandler{},
		LogMaxLines: logMaxLines,
	})
	h := &Host{
		ctx:                     options.Context,
		version:                 options.Version,
		buildEnvironment:        options.BuildEnvironment,
		logMaxLines:             logMaxLines,
		appHandler:              options.AppHandler,
		started:                 started,
		events:                  newEventBroadcaster(),
		services:                options.Services,
		fileLogSink:             options.FileLogSink,
		serverOptions:           options.ServerOptions,
		skipDefaultInterceptors: options.SkipDefaultInterceptors,
		onStuck:                 options.OnStuck,
	}
	return h, nil
}

// PublishServiceEvent broadcasts a service-lifecycle event to every
// SubscribeServiceEvents subscriber. Safe from any goroutine after NewHost.
func (h *Host) PublishServiceEvent(event *husiv1.SubscribeServiceEventsResponse) {
	if h == nil || h.events == nil {
		return
	}
	h.events.Publish(event)
}

func (h *Host) Started() *daemon.StartedService {
	return h.started
}

func (h *Host) SetAppHandler(handler AppHandler) {
	h.access.Lock()
	defer h.access.Unlock()
	h.appHandler = handler
}

func (h *Host) getAppHandler() AppHandler {
	h.access.Lock()
	defer h.access.Unlock()
	return h.appHandler
}

func (h *Host) Start(socketPath string) error {
	h.access.Lock()
	if h.listener != nil {
		h.access.Unlock()
		return os.ErrExist
	}
	h.access.Unlock()
	if socketPath == "" {
		return E.New("missing socket path")
	}

	err := clearSocketPath(socketPath)
	if err != nil {
		return err
	}
	// Copied from libbox / previous vario path: Android early-boot EROFS quirk.
	var listener net.Listener
	for range 30 {
		var unixListener *net.UnixListener
		unixListener, err = net.ListenUnix("unix", &net.UnixAddr{
			Name: socketPath,
			Net:  "unix",
		})
		if err == nil {
			listener = unixListener
			break
		}
		if !errors.Is(err, syscall.EROFS) {
			break
		}
		time.Sleep(1 * time.Second)
	}
	if err != nil {
		return E.Cause(err, "listen command server")
	}
	return h.StartOn(listener)
}

func clearSocketPath(socketPath string) error {
	_, err := os.Stat(socketPath)
	if err != nil {
		return nil
	}
	const liveHostTimeout = 500 * time.Millisecond
	conn, err := net.DialTimeout("unix", socketPath, liveHostTimeout)
	if err == nil {
		_ = conn.Close()
		return E.New("another core host is serving ", socketPath)
	}
	err = os.Remove(socketPath)
	if err != nil && !os.IsNotExist(err) {
		return E.Cause(err, "remove stale socket")
	}
	return nil
}

func (h *Host) NewServiceServer(serverOptions ...grpc.ServerOption) *grpc.Server {
	server := grpc.NewServer(serverOptions...)
	daemon.RegisterStartedServiceServer(server, h.started)
	husiv1.RegisterCoreServiceServer(server, &coreService{host: h})
	if h.appHandler != nil {
		husiv1.RegisterAppServiceServer(server, &appService{host: h})
	}

	healthServer := health.NewServer()
	ServingStatus(healthServer, daemon.StartedService_ServiceDesc.ServiceName)
	ServingStatus(healthServer, husiv1.CoreService_ServiceDesc.ServiceName)
	if h.appHandler != nil {
		ServingStatus(healthServer, husiv1.AppService_ServiceDesc.ServiceName)
	}
	for _, registrar := range h.services {
		registrar.RegisterServices(server, healthServer)
	}
	grpc_health_v1.RegisterHealthServer(server, healthServer)
	reflection.Register(server)
	return server
}

// StartOn serves the gRPC surface on an existing listener (UDS, named pipe, TCP).
// The Host takes ownership of the listener and closes it on Close.
func (h *Host) StartOn(listener net.Listener) error {
	h.access.Lock()
	defer h.access.Unlock()
	if h.listener != nil {
		return os.ErrExist
	}
	if listener == nil {
		return E.New("missing listener")
	}

	var serverOpts []grpc.ServerOption
	if !h.skipDefaultInterceptors {
		serverOpts = append(serverOpts,
			grpc.ChainUnaryInterceptor(daemon.UnaryLocaleInterceptor),
			grpc.ChainStreamInterceptor(daemon.StreamLocaleInterceptor),
		)
	}
	serverOpts = append(serverOpts, h.serverOptions...)
	server := h.NewServiceServer(serverOpts...)

	h.server = server
	h.listener = listener
	go func() {
		if serveErr := server.Serve(listener); serveErr != nil && !errors.Is(serveErr, grpc.ErrServerStopped) {
			if !E.IsClosed(serveErr) {
				log.Warn("gRPC server stopped: ", serveErr)
			}
		}
	}()
	return nil
}

func (h *Host) StartOrReload(ctx context.Context, config string) error {
	if h.Stuck() {
		return ErrHostStuck
	}
	if ctx == nil {
		ctx = context.Background()
	}
	err := h.started.StartOrReloadService(ctx, config, nil)
	if err != nil {
		return err
	}
	h.attachFileLogSink()
	return nil
}

func (h *Host) attachFileLogSink() {
	if h.fileLogSink == nil {
		return
	}
	instance := h.started.Instance()
	if instance == nil {
		log.Warn("file log sink: no instance after start")
		return
	}
	factory, ok := instance.Box().LogFactory().(log.ObservableFactory)
	if !ok {
		log.Warn("file log sink: log factory is not observable")
		return
	}
	factory.AttachPlatformWriter(h.fileLogSink)
}

func (h *Host) CloseService(timeout time.Duration) error {
	return h.closeServiceWithWatchdog(h.started.CloseService, timeout)
}

func (h *Host) Stuck() bool {
	return h.stuck.Load()
}

func (h *Host) closeServiceWithWatchdog(closeFn func() error, timeout time.Duration) error {
	if h.Stuck() {
		return ErrHostStuck
	}
	err := closeWithWatchdog(closeFn, timeout)
	if errors.Is(err, ErrCloseTimeout) {
		h.markStuck(err)
		return err
	}
	return err
}

func (h *Host) markStuck(err error) {
	if h.stuck.Swap(true) {
		return
	}
	log.Error(E.Cause(err, "core host is stuck"), ": the instance is still running and only a new process can replace it")
	if h.onStuck != nil {
		go h.onStuck(err)
	}
}

func closeWithWatchdog(closeFn func() error, timeout time.Duration) error {
	if timeout <= 0 {
		timeout = C.FatalStopTimeout
	}
	done := make(chan error, 1)
	go func() {
		done <- closeFn()
	}()
	select {
	case <-time.After(timeout):
		return ErrCloseTimeout
	case err := <-done:
		return err
	}
}

// InstanceContext returns the context of the running instance, falling back to
// the host context while there is none.
func (h *Host) InstanceContext() context.Context {
	return cmp.Or[context.Context](h.liveInstanceContext(), h.ctx)
}

func (h *Host) Close() error {
	h.access.Lock()
	defer h.access.Unlock()
	var errs []error
	if h.events != nil {
		h.events.Close()
	}
	if h.started != nil {
		// Close box may hang.
		err := h.closeServiceWithWatchdog(h.started.CloseService, C.FatalStopTimeout)
		if errors.Is(err, ErrHostStuck) {
			log.Warn("closing a stuck host: the running instance goes down with this process")
		}
		h.started.Close()
	}
	if h.server != nil {
		stopped := make(chan struct{})
		go func() {
			h.server.GracefulStop()
			close(stopped)
		}()
		select {
		case <-stopped:
		case <-time.After(3 * time.Second):
			h.server.Stop()
			<-stopped
		}
		h.server = nil
	}
	if h.listener != nil {
		// GracefulStop already closed a listener that Serve owned; closing
		// again only matters when the server never took it over.
		if err := h.listener.Close(); err != nil && !E.IsClosed(err) {
			errs = append(errs, err)
		}
		h.listener = nil
	}
	return E.Errors(errs...)
}

func (h *Host) HasInstance() bool {
	return h.started.Instance() != nil
}

func (h *Host) ListenerAddr() string {
	h.access.Lock()
	defer h.access.Unlock()
	if h.listener == nil {
		return ""
	}
	addr := h.listener.Addr()
	if addr == nil {
		return ""
	}
	return addr.String()
}
