package coreclient_test

import (
	"context"
	"net"
	"strings"
	"testing"
	"time"

	"github.com/sagernet/sing-box/daemon"
	N "github.com/sagernet/sing/common/network"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/coreclient"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

const remoteTestSecret = "test-secret"

func startRemoteAPI(t *testing.T, secret string) (addr string, cleanup func()) {
	t.Helper()
	listener, err := net.Listen(N.NetworkTCP, "127.0.0.1:0")
	require.NoError(t, err)

	server := grpc.NewServer(
		grpc.ChainUnaryInterceptor(remoteTestAuthUnary(secret)),
		grpc.ChainStreamInterceptor(remoteTestAuthStream(secret)),
	)
	healthServer := health.NewServer()
	healthServer.SetServingStatus("", grpc_health_v1.HealthCheckResponse_SERVING)
	healthServer.SetServingStatus(daemon.StartedService_ServiceDesc.ServiceName, grpc_health_v1.HealthCheckResponse_SERVING)
	grpc_health_v1.RegisterHealthServer(server, healthServer)
	registerEcho(server)

	go func() {
		_ = server.Serve(listener)
	}()
	return listener.Addr().String(), func() {
		server.Stop()
		_ = listener.Close()
	}
}

func remoteTestAuthUnary(secret string) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, request any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		if err := remoteTestAuthenticate(ctx, secret); err != nil {
			return nil, err
		}
		return handler(ctx, request)
	}
}

func remoteTestAuthStream(secret string) grpc.StreamServerInterceptor {
	return func(server any, stream grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
		if err := remoteTestAuthenticate(stream.Context(), secret); err != nil {
			return err
		}
		return handler(server, stream)
	}
}

func remoteTestAuthenticate(ctx context.Context, secret string) error {
	if secret == "" {
		return nil
	}
	md, loaded := metadata.FromIncomingContext(ctx)
	if !loaded {
		return status.Error(codes.Unauthenticated, "missing metadata")
	}
	values := md.Get("authorization")
	if len(values) == 0 {
		return status.Error(codes.Unauthenticated, "missing authorization")
	}
	token, isBearer := strings.CutPrefix(values[0], "Bearer ")
	if !isBearer || token != secret {
		return status.Error(codes.Unauthenticated, "invalid authorization")
	}
	return nil
}

func registerEcho(server *grpc.Server) {
	const fullMethod = "/test.Echo/Echo"
	server.RegisterService(&grpc.ServiceDesc{
		ServiceName: "test.Echo",
		HandlerType: (*any)(nil),
		Methods: []grpc.MethodDesc{{
			MethodName: "Echo",
			Handler: func(srv any, ctx context.Context, dec func(any) error, interceptor grpc.UnaryServerInterceptor) (any, error) {
				in := new(daemon.Version)
				if err := dec(in); err != nil {
					return nil, err
				}
				handler := func(context.Context, any) (any, error) {
					return in, nil
				}
				if interceptor == nil {
					return handler(ctx, in)
				}
				return interceptor(ctx, in, &grpc.UnaryServerInfo{
					Server:     srv,
					FullMethod: fullMethod,
				}, handler)
			},
		}},
	}, struct{}{})
}

func TestDialRemoteProbeAndEcho(t *testing.T) {
	addr, cleanup := startRemoteAPI(t, remoteTestSecret)
	defer cleanup()

	client, err := coreclient.DialRemote("http://"+addr, remoteTestSecret)
	require.NoError(t, err)
	defer client.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	require.NoError(t, client.Probe(ctx))

	const payload = "raw-codec-payload"
	req, err := proto.Marshal(&daemon.Version{Version: payload, ApiVersion: 42})
	require.NoError(t, err)
	respBytes, err := client.Invoke(ctx, "/test.Echo/Echo", req)
	require.NoError(t, err)
	var version daemon.Version
	require.NoError(t, proto.Unmarshal(respBytes, &version))
	assert.Equal(t, payload, version.GetVersion())
	assert.Equal(t, int32(42), version.GetApiVersion())
}

func TestDialRemoteWrongSecretUnauthenticated(t *testing.T) {
	addr, cleanup := startRemoteAPI(t, remoteTestSecret)
	defer cleanup()

	client, err := coreclient.DialRemote("http://"+addr, "wrong-secret")
	require.NoError(t, err)
	defer client.Close()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	err = client.Probe(ctx)
	require.Error(t, err)
	st, ok := status.FromError(err)
	require.True(t, ok, "expected gRPC status, got %v", err)
	assert.Equal(t, codes.Unauthenticated, st.Code())
}

func TestDialRemoteMissingURL(t *testing.T) {
	_, err := coreclient.DialRemote("", remoteTestSecret)
	require.Error(t, err)
}
