package externalapi_test

import (
	"bufio"
	"bytes"
	"context"
	"encoding/binary"
	"io"
	"net"
	"net/http"
	"net/textproto"
	"strings"
	"testing"
	"time"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	N "github.com/sagernet/sing/common/network"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/externalapi"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

const testSecret = "test-secret"

func newHealthServer(t *testing.T, interceptors ...grpc.ServerOption) *grpc.Server {
	t.Helper()
	server := grpc.NewServer(interceptors...)
	healthServer := health.NewServer()
	healthServer.SetServingStatus("", grpc_health_v1.HealthCheckResponse_SERVING)
	grpc_health_v1.RegisterHealthServer(server, healthServer)
	return server
}

func startAPI(t *testing.T, options externalapi.Options, grpcServer *grpc.Server) *externalapi.Server {
	t.Helper()
	server, err := externalapi.New(t.Context(), log.NewNOPFactory().Logger(), options, grpcServer)
	require.NoError(t, err)
	t.Cleanup(func() {
		_ = server.Close()
		grpcServer.Stop()
	})
	require.NoError(t, server.Start())
	waitTCP(t, server.Addr())
	return server
}

func waitTCP(t *testing.T, addr string) {
	t.Helper()
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		conn, err := net.Dial(N.NetworkTCP, addr)
		if err == nil {
			_ = conn.Close()
			return
		}
		time.Sleep(20 * time.Millisecond)
	}
	t.Fatalf("external api never accepted on %s", addr)
}

func dialGRPC(t *testing.T, addr string) *grpc.ClientConn {
	t.Helper()
	conn, err := grpc.NewClient(addr, grpc.WithTransportCredentials(insecure.NewCredentials()))
	require.NoError(t, err)
	t.Cleanup(func() { _ = conn.Close() })
	return conn
}

func TestServerNativeGRPC(t *testing.T) {
	grpcServer := newHealthServer(t)
	api := startAPI(t, externalapi.Options{}, grpcServer)

	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()
	resp, err := grpc_health_v1.NewHealthClient(dialGRPC(t, api.Addr())).Check(ctx, &grpc_health_v1.HealthCheckRequest{})
	require.NoError(t, err)
	assert.Equal(t, grpc_health_v1.HealthCheckResponse_SERVING, resp.GetStatus())
}

func TestServerGRPCWeb(t *testing.T) {
	grpcServer := newHealthServer(t)
	api := startAPI(t, externalapi.Options{}, grpcServer)

	httpRequest, err := http.NewRequestWithContext(
		t.Context(),
		http.MethodPost,
		"http://"+api.Addr()+"/grpc.health.v1.Health/Check",
		bytes.NewReader(grpcWebFrame(0, nil)),
	)
	require.NoError(t, err)
	httpRequest.Header.Set("Content-Type", "application/grpc-web+proto")
	httpRequest.Header.Set("X-Grpc-Web", "1")

	httpResponse, err := (&http.Client{Timeout: 5 * time.Second}).Do(httpRequest)
	require.NoError(t, err)
	t.Cleanup(func() { _ = httpResponse.Body.Close() })
	require.Equal(t, http.StatusOK, httpResponse.StatusCode)
	assert.True(t, strings.HasPrefix(httpResponse.Header.Get("Content-Type"), "application/grpc-web"))

	body, err := io.ReadAll(httpResponse.Body)
	require.NoError(t, err)

	messages, trailer := parseGRPCWeb(t, body)
	require.NotEmpty(t, messages, "expected a gRPC-Web data frame")
	var healthResponse grpc_health_v1.HealthCheckResponse
	require.NoError(t, proto.Unmarshal(messages[0], &healthResponse))
	assert.Equal(t, grpc_health_v1.HealthCheckResponse_SERVING, healthResponse.GetStatus())
	require.NotNil(t, trailer)
	assert.Equal(t, "0", trailer.Get("grpc-status"))
}

func TestServerSecretAuth(t *testing.T) {
	grpcServer := newHealthServer(t,
		grpc.ChainUnaryInterceptor(externalapi.UnaryAuthInterceptor(testSecret)),
		grpc.ChainStreamInterceptor(externalapi.StreamAuthInterceptor(testSecret)),
	)
	api := startAPI(t, externalapi.Options{Secret: testSecret}, grpcServer)
	client := grpc_health_v1.NewHealthClient(dialGRPC(t, api.Addr()))

	ctx, cancel := context.WithTimeout(t.Context(), 5*time.Second)
	defer cancel()
	_, err := client.Check(ctx, &grpc_health_v1.HealthCheckRequest{})
	require.Error(t, err)
	st, ok := status.FromError(err)
	require.True(t, ok, "expected grpc status, got %v", err)
	assert.Equal(t, codes.Unauthenticated, st.Code())

	authed := metadata.NewOutgoingContext(ctx, metadata.Pairs("authorization", "Bearer "+testSecret))
	resp, err := client.Check(authed, &grpc_health_v1.HealthCheckRequest{})
	require.NoError(t, err)
	assert.Equal(t, grpc_health_v1.HealthCheckResponse_SERVING, resp.GetStatus())
}

func TestOptionsValidateACME(t *testing.T) {
	tests := []struct {
		name    string
		options externalapi.Options
		want    string
	}{
		{
			name: "acme",
			options: externalapi.Options{
				InboundTLSOptionsContainer: option.InboundTLSOptionsContainer{
					TLS: &option.InboundTLSOptions{
						Enabled: true,
						ACME:    &option.InboundACMEOptions{},
					},
				},
			},
			want: "acme",
		},
		{
			name: "certificate_provider",
			options: externalapi.Options{
				InboundTLSOptionsContainer: option.InboundTLSOptionsContainer{
					TLS: &option.InboundTLSOptions{
						Enabled:             true,
						CertificateProvider: &option.CertificateProviderOptions{},
					},
				},
			},
			want: "certificate_provider",
		},
		{
			name: "bind_interface",
			options: externalapi.Options{
				ListenOptions: option.ListenOptions{BindInterface: "eth0"},
			},
			want: "bind_interface",
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			err := tt.options.Validate()
			require.Error(t, err)
			assert.Contains(t, err.Error(), tt.want)
		})
	}
	assert.NoError(t, externalapi.Options{}.Validate())
}

func grpcWebFrame(flags byte, payload []byte) []byte {
	frame := []byte{flags, 0, 0, 0, 0}
	binary.BigEndian.PutUint32(frame[1:], uint32(len(payload)))
	return append(frame, payload...)
}

func parseGRPCWeb(t *testing.T, body []byte) (messages [][]byte, trailer http.Header) {
	t.Helper()
	for len(body) >= 5 {
		flags := body[0]
		length := binary.BigEndian.Uint32(body[1:5])
		body = body[5:]
		require.GreaterOrEqual(t, uint32(len(body)), length, "truncated gRPC-Web frame")
		payload := body[:length]
		body = body[length:]
		if flags&0x80 != 0 {
			reader := textproto.NewReader(bufio.NewReader(io.MultiReader(bytes.NewReader(payload), strings.NewReader("\r\n"))))
			mimeHeader, err := reader.ReadMIMEHeader()
			require.NoError(t, err)
			trailer = http.Header(mimeHeader)
			continue
		}
		messages = append(messages, payload)
	}
	return messages, trailer
}
