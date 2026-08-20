package coresvc

import (
	"cmp"
	"context"
	"errors"
	"net"
	"os"
	"sync"
	"syscall"
	"time"

	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/reflection"
)

type Host struct {
	access sync.Mutex

	ctx         context.Context
	version     string
	logMaxLines int
	appHandler  AppHandler

	started *daemon.StartedService
	holder  *InstanceContextHolder
	events  *eventBroadcaster

	backend       Backend
	extraServices ExtraServiceRegistrar
	fileLogSink   log.PlatformWriter
	serverOptions []grpc.ServerOption
	// skipDefaultInterceptors omits the built-in locale interceptors when the
	// caller already chains them with auth in ServerOptions.
	skipDefaultInterceptors bool

	server   *grpc.Server
	listener net.Listener
}

type HostOptions struct {
	Context     context.Context
	Version     string
	LogMaxLines int
	AppHandler  AppHandler

	Backend Backend
	// FileLogSink is attached after a successful StartOrReloadService so box
	// logs also land in stderr.log. Optional.
	FileLogSink   log.PlatformWriter
	ExtraServices ExtraServiceRegistrar
	ServerOptions []grpc.ServerOption
	// SkipDefaultInterceptors skips the built-in locale interceptors. Use when
	// ServerOptions already chain locale + auth in the desired order.
	SkipDefaultInterceptors bool
}

// NewHost constructs a Host. Call Start to listen; Close to tear down.
func NewHost(options HostOptions) (*Host, error) {
	if options.Context == nil {
		return nil, E.New("missing context")
	}
	logMaxLines := max(options.LogMaxLines, 50)
	holder := service.FromContext[*InstanceContextHolder](options.Context)
	if holder == nil {
		holder = NewInstanceContextHolder()
		service.MustRegister[*InstanceContextHolder](options.Context, holder)
	}
	started := daemon.NewStartedService(daemon.ServiceOptions{
		Context:     options.Context,
		Handler:     platformHandler{},
		LogMaxLines: logMaxLines,
	})
	backend := cmp.Or[Backend](options.Backend, UnimplementedBackend{})
	h := &Host{
		ctx:                     options.Context,
		version:                 options.Version,
		logMaxLines:             logMaxLines,
		appHandler:              options.AppHandler,
		started:                 started,
		holder:                  holder,
		events:                  newEventBroadcaster(),
		backend:                 backend,
		extraServices:           options.ExtraServices,
		fileLogSink:             options.FileLogSink,
		serverOptions:           options.ServerOptions,
		skipDefaultInterceptors: options.SkipDefaultInterceptors,
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

func (h *Host) Holder() *InstanceContextHolder {
	return h.holder
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

	_ = os.Remove(socketPath)
	// Copied from libbox / previous vario path: Android early-boot EROFS quirk.
	var (
		listener net.Listener
		err      error
	)
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
	server := grpc.NewServer(serverOpts...)
	daemon.RegisterStartedServiceServer(server, h.started)
	husiv1.RegisterCoreServiceServer(server, &coreService{host: h})
	husiv1.RegisterApplicationServiceServer(server, &applicationService{host: h})
	if h.appHandler != nil {
		husiv1.RegisterAppServiceServer(server, &appService{host: h})
	}

	healthServer := health.NewServer()
	healthServer.SetServingStatus(daemon.StartedService_ServiceDesc.ServiceName, grpc_health_v1.HealthCheckResponse_SERVING)
	healthServer.SetServingStatus(husiv1.CoreService_ServiceDesc.ServiceName, grpc_health_v1.HealthCheckResponse_SERVING)
	healthServer.SetServingStatus(husiv1.ApplicationService_ServiceDesc.ServiceName, grpc_health_v1.HealthCheckResponse_SERVING)
	if h.appHandler != nil {
		healthServer.SetServingStatus(husiv1.AppService_ServiceDesc.ServiceName, grpc_health_v1.HealthCheckResponse_SERVING)
	}
	if h.extraServices != nil {
		h.extraServices.RegisterExtraServices(server, healthServer)
	}
	grpc_health_v1.RegisterHealthServer(server, healthServer)
	reflection.Register(server)

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
	return closeWithWatchdog(h.started.CloseService, timeout)
}

func closeWithWatchdog(closeFn func() error, timeout time.Duration) error {
	if timeout <= 0 {
		timeout = 10 * time.Second
	}
	done := make(chan error, 1)
	go func() {
		done <- closeFn()
	}()
	select {
	case <-time.After(timeout):
		return E.New("sing-box did not close in time")
	case err := <-done:
		return err
	}
}

func (h *Host) Close() error {
	h.access.Lock()
	defer h.access.Unlock()
	var errs []error
	if h.events != nil {
		h.events.Close()
	}
	if h.started != nil {
		_ = h.started.CloseService()
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
