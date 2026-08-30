package coresvc_test

import (
	"context"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"testing"
	"testing/synctest"
	"time"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing/service"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/distro"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/emptypb"
)

func testBaseContext(t *testing.T) context.Context {
	t.Helper()
	ctx := box.Context(
		t.Context(),
		distro.InboundRegistry(),
		distro.OutboundRegistry(),
		distro.EndpointRegistry(),
		distro.DNSTransportRegistry(),
		distro.ServiceRegistry(),
		distro.CertificateProviderRegistry(),
	)
	holder := coresvc.NewInstanceContextHolder()
	service.MustRegister[*coresvc.InstanceContextHolder](ctx, holder)
	return ctx
}

func startTestHost(t *testing.T, opts coresvc.HostOptions) (*coresvc.Host, string) {
	t.Helper()
	if opts.Context == nil {
		opts.Context = testBaseContext(t)
	}
	if opts.Version == "" {
		opts.Version = "test"
	}
	if opts.LogMaxLines == 0 {
		opts.LogMaxLines = 100
	}
	if opts.BuildEnvironment == "" {
		opts.BuildEnvironment = "test-env"
	}
	host, err := coresvc.NewHost(opts)
	require.NoError(t, err)
	socketPath := filepath.Join(t.TempDir(), coresvc.Socket)
	require.NoError(t, host.Start(socketPath))
	t.Cleanup(func() { _ = host.Close() })
	// Wait briefly for Serve to accept.
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		conn, err := net.Dial("unix", socketPath)
		if err == nil {
			_ = conn.Close()
			break
		}
		time.Sleep(20 * time.Millisecond)
	}
	return host, socketPath
}

func dialGRPC(t *testing.T, socketPath string) *grpc.ClientConn {
	t.Helper()
	conn, err := grpc.NewClient(
		"unix:"+socketPath,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithContextDialer(func(ctx context.Context, addr string) (net.Conn, error) {
			var d net.Dialer
			return d.DialContext(ctx, "unix", socketPath)
		}),
	)
	require.NoError(t, err)
	t.Cleanup(func() { _ = conn.Close() })
	return conn
}

func TestHostHealthAndGetVersion(t *testing.T) {
	host, socketPath := startTestHost(t, coresvc.HostOptions{})
	_ = host
	conn := dialGRPC(t, socketPath)

	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()

	healthClient := grpc_health_v1.NewHealthClient(conn)
	resp, err := healthClient.Check(ctx, &grpc_health_v1.HealthCheckRequest{})
	require.NoError(t, err)
	assert.Equal(t, grpc_health_v1.HealthCheckResponse_SERVING, resp.GetStatus())

	daemonClient := daemon.NewStartedServiceClient(conn)
	version, err := daemonClient.GetVersion(ctx, &emptypb.Empty{})
	require.NoError(t, err)
	assert.NotEmpty(t, version.GetVersion(), "empty sing-box version")

	coreClient := husiv1.NewCoreServiceClient(conn)
	husiVersion, err := coreClient.GetVersion(ctx, &husiv1.GetVersionRequest{})
	require.NoError(t, err)
	assert.Equal(t, "test", husiVersion.GetVersion())
	assert.Equal(t, "test-env", husiVersion.GetBuildEnvironment())
	assert.Equal(t, uint32(daemon.APIVersion), husiVersion.GetApiVersion())
}

func TestHostCloseAfterStartReturnsNil(t *testing.T) {
	host, socketPath := startTestHost(t, coresvc.HostOptions{})
	conn := dialGRPC(t, socketPath)

	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()

	// A served RPC guarantees grpc.Server owns the listener, so Close must
	// tolerate GracefulStop having already closed it.
	healthClient := grpc_health_v1.NewHealthClient(conn)
	_, err := healthClient.Check(ctx, &grpc_health_v1.HealthCheckRequest{})
	require.NoError(t, err)

	assert.NoError(t, host.Close())
}

// The Host serves no application surface of its own: a host built without the
// registrar libcore provides answers Unimplemented rather than a stub value.
func TestApplicationServiceUnregistered(t *testing.T) {
	_, socketPath := startTestHost(t, coresvc.HostOptions{})
	conn := dialGRPC(t, socketPath)
	client := husiv1.NewApplicationServiceClient(conn)
	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()

	_, err := client.CheckConfig(ctx, &husiv1.CheckConfigRequest{Config: "{}"})
	require.Error(t, err, "expected Unimplemented without an application service")
	st, ok := status.FromError(err)
	require.True(t, ok, "expected grpc status, got %v", err)
	assert.Equal(t, codes.Unimplemented, st.Code())
}

// registeredService is any extra surface a host owner contributes.
type registeredService struct {
	husiv1.UnimplementedApplicationServiceServer
	schema string
}

func (s registeredService) RegisterServices(server *grpc.Server, healthServer *health.Server) {
	husiv1.RegisterApplicationServiceServer(server, s)
	coresvc.ServingStatus(healthServer, husiv1.ApplicationService_ServiceDesc.ServiceName)
}

func (s registeredService) GenerateSchema(_ context.Context, _ *husiv1.GenerateSchemaRequest) (*husiv1.GenerateSchemaResponse, error) {
	return &husiv1.GenerateSchemaResponse{Schema: s.schema}, nil
}

func TestServiceRegistrarIsServed(t *testing.T) {
	const schema = `{"type":"object"}`
	_, socketPath := startTestHost(t, coresvc.HostOptions{
		Services: []coresvc.ServiceRegistrar{registeredService{schema: schema}},
	})
	conn := dialGRPC(t, socketPath)
	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()

	resp, err := husiv1.NewApplicationServiceClient(conn).GenerateSchema(ctx, &husiv1.GenerateSchemaRequest{
		Kind: husiv1.SchemaKind_SCHEMA_KIND_CONFIG,
	})
	require.NoError(t, err)
	assert.Equal(t, schema, resp.GetSchema())

	healthResp, err := grpc_health_v1.NewHealthClient(conn).Check(ctx, &grpc_health_v1.HealthCheckRequest{
		Service: husiv1.ApplicationService_ServiceDesc.ServiceName,
	})
	require.NoError(t, err)
	assert.Equal(t, grpc_health_v1.HealthCheckResponse_SERVING, healthResp.GetStatus())
}

func TestStatusStreamIdleSnapshot(t *testing.T) {
	_, socketPath := startTestHost(t, coresvc.HostOptions{})
	conn := dialGRPC(t, socketPath)
	client := daemon.NewStartedServiceClient(conn)
	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()

	stream, err := client.SubscribeServiceStatus(ctx, &emptypb.Empty{})
	require.NoError(t, err)
	status, err := stream.Recv()
	require.NoError(t, err)
	assert.Equal(t, daemon.ServiceStatus_IDLE, status.GetStatus())
}

func TestStartMinimalConfigAndURLTest(t *testing.T) {
	// Instance may write cache.db into the process working directory.
	t.Chdir(t.TempDir())

	// Local HTTP server for the url test.
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(t, err)
	t.Cleanup(func() { _ = ln.Close() })
	go http.Serve(ln, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	testURL := "http://" + ln.Addr().String() + "/"

	host, socketPath := startTestHost(t, coresvc.HostOptions{})
	config := `{
  "log": {"level": "warn"},
  "outbounds": [{"type": "direct", "tag": "direct"}]
}`
	require.NoError(t, host.StartOrReload(t.Context(), config))
	require.True(t, host.HasInstance(), "expected instance after start")

	conn := dialGRPC(t, socketPath)
	core := husiv1.NewCoreServiceClient(conn)
	ctx, cancel := context.WithTimeout(t.Context(), 15*time.Second)
	defer cancel()

	resp, err := core.URLTest(ctx, &husiv1.URLTestRequest{
		OutboundTag: "direct",
		Link:        testURL,
		TimeoutMs:   5000,
	})
	require.NoError(t, err)
	assert.GreaterOrEqual(t, resp.GetLatencyMs(), int32(0))

	require.NoError(t, host.CloseService(5*time.Second))
}

func TestCloseWithWatchdogTimeout(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		closed := make(chan struct{})
		err := coresvc.CloseWithWatchdogForTest(func() error {
			defer close(closed)
			time.Sleep(500 * time.Millisecond)
			return nil
		}, 50*time.Millisecond)
		require.EqualError(t, err, "sing-box did not close in time")
		<-closed
	})
}

func TestCloseWithWatchdogSuccess(t *testing.T) {
	err := coresvc.CloseWithWatchdogForTest(func() error {
		return nil
	}, time.Second)
	require.NoError(t, err)
}

func TestURLTestNotFound(t *testing.T) {
	t.Chdir(t.TempDir())
	host, socketPath := startTestHost(t, coresvc.HostOptions{})
	config := `{
  "log": {"level": "warn"},
  "outbounds": [{"type": "direct", "tag": "direct"}]
}`
	require.NoError(t, host.StartOrReload(t.Context(), config))
	conn := dialGRPC(t, socketPath)
	core := husiv1.NewCoreServiceClient(conn)
	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()
	_, err := core.URLTest(ctx, &husiv1.URLTestRequest{
		OutboundTag: "missing-tag",
		Link:        "http://127.0.0.1/",
		TimeoutMs:   1000,
	})
	require.Error(t, err, "expected NotFound for missing outbound")
	st, ok := status.FromError(err)
	require.True(t, ok, "expected grpc status, got %v", err)
	assert.Equal(t, codes.NotFound, st.Code())
}

func TestHolderStartFailThenStartOK(t *testing.T) {
	t.Chdir(t.TempDir())
	host, socketPath := startTestHost(t, coresvc.HostOptions{})

	// First start fails (invalid config).
	require.Error(t, host.StartOrReload(t.Context(), `{not json`), "expected StartOrReload to fail on invalid config")

	// Second start succeeds; URLTest must use the fresh instance context.
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	require.NoError(t, err)
	t.Cleanup(func() { _ = ln.Close() })
	go http.Serve(ln, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusNoContent)
	}))
	testURL := "http://" + ln.Addr().String() + "/"

	config := `{
  "log": {"level": "warn"},
  "outbounds": [{"type": "direct", "tag": "direct"}]
}`
	require.NoError(t, host.StartOrReload(t.Context(), config))
	conn := dialGRPC(t, socketPath)
	core := husiv1.NewCoreServiceClient(conn)
	ctx, cancel := context.WithTimeout(t.Context(), 15*time.Second)
	defer cancel()
	resp, err := core.URLTest(ctx, &husiv1.URLTestRequest{
		OutboundTag: "direct",
		Link:        testURL,
		TimeoutMs:   5000,
	})
	require.NoError(t, err)
	assert.GreaterOrEqual(t, resp.GetLatencyMs(), int32(0))
}

func TestHostStuckAfterCloseTimeout(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		stuck := make(chan error, 4)
		host, err := coresvc.NewHost(coresvc.HostOptions{
			Context: testBaseContext(t),
			OnStuck: func(err error) { stuck <- err },
		})
		require.NoError(t, err)
		assert.False(t, host.Stuck())

		hang := make(chan struct{})
		t.Cleanup(func() { _ = host.Close() })
		t.Cleanup(func() { close(hang) })
		err = host.CloseServiceWithWatchdogForTest(func() error {
			<-hang
			return nil
		}, 50*time.Millisecond)
		require.ErrorIs(t, err, coresvc.ErrCloseTimeout)
		assert.True(t, host.Stuck())

		notified := <-stuck
		require.ErrorIs(t, notified, coresvc.ErrCloseTimeout)

		// Every later lifecycle call fails fast instead of queueing behind the
		// lock the abandoned close still holds.
		start := time.Now()
		require.ErrorIs(t, host.CloseService(time.Hour), coresvc.ErrHostStuck)
		require.ErrorIs(t, host.StartOrReload(t.Context(), `{}`), coresvc.ErrHostStuck)
		assert.Less(t, time.Since(start), 5*time.Second)

		synctest.Wait()
		select {
		case <-stuck:
			t.Fatal("OnStuck was called more than once")
		default:
		}
	})
}

func newTestHost(t *testing.T) *coresvc.Host {
	t.Helper()
	host, err := coresvc.NewHost(coresvc.HostOptions{
		Context:          testBaseContext(t),
		Version:          "test",
		LogMaxLines:      100,
		BuildEnvironment: "test-env",
	})
	require.NoError(t, err)
	t.Cleanup(func() { _ = host.Close() })
	return host
}

func TestStartRefusesSocketServedByAnotherHost(t *testing.T) {
	_, socketPath := startTestHost(t, coresvc.HostOptions{})

	err := newTestHost(t).Start(socketPath)
	require.ErrorContains(t, err, "another core host is serving")

	// The first host still owns the socket it was serving.
	conn, err := net.Dial("unix", socketPath)
	require.NoError(t, err)
	_ = conn.Close()
}

func TestStartClearsStaleSocket(t *testing.T) {
	socketPath := filepath.Join(t.TempDir(), coresvc.Socket)
	require.NoError(t, os.WriteFile(socketPath, nil, 0o600))

	require.NoError(t, newTestHost(t).Start(socketPath))

	conn, err := net.Dial("unix", socketPath)
	require.NoError(t, err)
	_ = conn.Close()
}
