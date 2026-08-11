package coreclient_test

import (
	"context"
	"net"
	"path/filepath"
	"runtime"
	"sync/atomic"
	"testing"
	"time"

	"libcore/coreclient"
	"libcore/coresvc"
	"libcore/distro"
	"libcore/pb/husi/v1"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing/service"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/emptypb"
)

func startHost(t *testing.T) (socketPath string, cleanup func()) {
	t.Helper()
	ctx := box.Context(
		context.Background(),
		distro.InboundRegistry(),
		distro.OutboundRegistry(),
		distro.EndpointRegistry(),
		distro.DNSTransportRegistry(),
		distro.ServiceRegistry(),
		distro.CertificateProviderRegistry(),
	)
	service.MustRegister[*coresvc.InstanceContextHolder](ctx, coresvc.NewInstanceContextHolder())
	host, err := coresvc.NewHost(coresvc.HostOptions{
		Context:     ctx,
		Version:     "bridge-test",
		LogMaxLines: 50,
		CheckConfig: func(string) error { return nil },
		GenerateSchema: func(husiv1.SchemaKind) (string, error) {
			return "{}", nil
		},
		BuildEnvironment: func() string { return "test" },
	})
	require.NoError(t, err)
	socketPath = filepath.Join(t.TempDir(), coresvc.Socket)
	require.NoError(t, host.Start(socketPath))
	// Wait for accept.
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		conn, err := net.Dial("unix", socketPath)
		if err == nil {
			_ = conn.Close()
			break
		}
		time.Sleep(20 * time.Millisecond)
	}
	return socketPath, func() { _ = host.Close() }
}

func TestBridgeInvokeGetVersion(t *testing.T) {
	socketPath, cleanup := startHost(t)
	defer cleanup()

	client, err := coreclient.Dial(socketPath)
	require.NoError(t, err)
	defer client.Close()

	req, err := proto.Marshal(&emptypb.Empty{})
	require.NoError(t, err)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	respBytes, err := client.Invoke(ctx, daemon.StartedService_GetVersion_FullMethodName, req)
	require.NoError(t, err)
	var version daemon.Version
	require.NoError(t, proto.Unmarshal(respBytes, &version))
	assert.NotEmpty(t, version.GetVersion())
}

func TestBridgeStreamServiceStatus(t *testing.T) {
	socketPath, cleanup := startHost(t)
	defer cleanup()

	client, err := coreclient.Dial(socketPath)
	require.NoError(t, err)
	defer client.Close()

	req, _ := proto.Marshal(&emptypb.Empty{})
	var got atomic.Int32
	done := make(chan struct{})
	handler := streamHandler{
		onMessage: func(msg []byte) {
			var status daemon.ServiceStatus
			if err := proto.Unmarshal(msg, &status); err != nil {
				assert.NoError(t, err, "unmarshal status")
				return
			}
			if status.GetStatus() == daemon.ServiceStatus_IDLE {
				got.Store(1)
			}
		},
		onClosed: func(string) { close(done) },
	}
	stream, err := client.Stream(context.Background(), daemon.StartedService_SubscribeServiceStatus_FullMethodName, req, handler)
	require.NoError(t, err)
	// Wait for first message.
	deadline := time.Now().Add(3 * time.Second)
	for time.Now().Before(deadline) && got.Load() == 0 {
		time.Sleep(20 * time.Millisecond)
	}
	require.NotZero(t, got.Load(), "did not receive IDLE status")
	stream.Close()
	select {
	case <-done:
	case <-time.After(2 * time.Second):
		require.FailNow(t, "stream did not close")
	}
}

func TestProbeAgainstStoppedSocket(t *testing.T) {
	// Dial a path that will never accept — NewClient succeeds lazily; Probe fails.
	path := filepath.Join(t.TempDir(), "missing.sock")
	client, err := coreclient.Dial(path)
	if err != nil {
		// grpc.NewClient is lazy; if it fails eagerly that is also fine.
		return
	}
	defer client.Close()
	ctx, cancel := context.WithTimeout(context.Background(), 500*time.Millisecond)
	defer cancel()
	require.Error(t, client.Probe(ctx), "expected probe failure against missing socket")
}

type streamHandler struct {
	onMessage func([]byte)
	onClosed  func(string)
}

func (h streamHandler) OnMessage(message []byte) { h.onMessage(message) }
func (h streamHandler) OnClosed(errMessage string) {
	if h.onClosed != nil {
		h.onClosed(errMessage)
	}
}

func TestStreamCloseReleasesGoroutine(t *testing.T) {
	socketPath, cleanup := startHost(t)
	defer cleanup()

	client, err := coreclient.Dial(socketPath)
	require.NoError(t, err)
	defer client.Close()

	// Warm up.
	req, _ := proto.Marshal(&emptypb.Empty{})
	runtime.GC()
	before := runtime.NumGoroutine()

	const N = 20
	for i := 0; i < N; i++ {
		done := make(chan struct{})
		handler := streamHandler{
			onMessage: func([]byte) {},
			onClosed:  func(string) { close(done) },
		}
		stream, err := client.Stream(context.Background(), daemon.StartedService_SubscribeServiceStatus_FullMethodName, req, handler)
		require.NoError(t, err)
		stream.Close()
		select {
		case <-done:
		case <-time.After(2 * time.Second):
			require.FailNow(t, "stream did not close")
		}
	}

	// Allow schedulers to settle.
	deadline := time.Now().Add(2 * time.Second)
	var after int
	for time.Now().Before(deadline) {
		runtime.GC()
		after = runtime.NumGoroutine()
		// Tolerate a small amount of noise; must not leak one goroutine per stream.
		if after <= before+5 {
			return
		}
		time.Sleep(50 * time.Millisecond)
	}
	require.LessOrEqual(t, after, before+5, "goroutine leak: before=%d after=%d (opened %d streams)", before, after, N)
}
