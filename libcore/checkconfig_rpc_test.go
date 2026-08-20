package libcore

import (
	"context"
	"net"
	"path/filepath"
	"testing"
	"time"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing/service"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/distro"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/status"
)

type testHostBackend struct {
	HostBackend
}

func (testHostBackend) BuildEnvironment() string { return "test" }

func startLibcoreHost(t *testing.T) (*coresvc.Host, string) {
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
	service.MustRegister[*coresvc.InstanceContextHolder](ctx, coresvc.NewInstanceContextHolder())
	host, err := coresvc.NewHost(coresvc.HostOptions{
		Context:     ctx,
		Version:     "check-test",
		LogMaxLines: 50,
		Backend:     testHostBackend{},
	})
	require.NoError(t, err)
	socketPath := filepath.Join(t.TempDir(), coresvc.Socket)
	require.NoError(t, host.Start(socketPath))
	t.Cleanup(func() { _ = host.Close() })
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		conn, dialErr := net.Dial("unix", socketPath)
		if dialErr == nil {
			_ = conn.Close()
			break
		}
		time.Sleep(20 * time.Millisecond)
	}
	return host, socketPath
}

func TestRealCheckConfigInvalidViaRPC(t *testing.T) {
	_, socketPath := startLibcoreHost(t)
	conn, err := grpc.NewClient(
		"unix:"+socketPath,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithContextDialer(func(ctx context.Context, addr string) (net.Conn, error) {
			var dialer net.Dialer
			return dialer.DialContext(ctx, "unix", socketPath)
		}),
	)
	require.NoError(t, err)
	t.Cleanup(func() { _ = conn.Close() })

	client := husiv1.NewApplicationServiceClient(conn)
	ctx, cancel := context.WithTimeout(t.Context(), 15*time.Second)
	defer cancel()

	_, err = client.CheckConfig(ctx, &husiv1.CheckConfigRequest{Config: "not-json"})
	require.Error(t, err, "expected InvalidArgument for invalid config")
	st, ok := status.FromError(err)
	require.True(t, ok, "expected grpc status, got %v", err)
	assert.Equal(t, codes.InvalidArgument, st.Code())
	assert.NotEmpty(t, st.Message(), "expected parser message")
}

func TestRealGenerateSchemaViaRPC(t *testing.T) {
	_, socketPath := startLibcoreHost(t)
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

	client := husiv1.NewApplicationServiceClient(conn)
	ctx, cancel := context.WithTimeout(t.Context(), 30*time.Second)
	defer cancel()

	for _, kind := range []husiv1.SchemaKind{
		husiv1.SchemaKind_SCHEMA_KIND_CONFIG,
		husiv1.SchemaKind_SCHEMA_KIND_OUTBOUND,
		husiv1.SchemaKind_SCHEMA_KIND_DNS_RULE,
	} {
		resp, err := client.GenerateSchema(ctx, &husiv1.GenerateSchemaRequest{Kind: kind})
		require.NoError(t, err, "GenerateSchema %v", kind)
		require.NotEmpty(t, resp.GetSchema(), "empty schema for %v", kind)
		assert.Equal(t, byte('{'), resp.GetSchema()[0], "schema for %v does not look like JSON object", kind)
	}
}
