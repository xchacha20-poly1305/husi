package externalapi

import (
	"context"
	"strings"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

func UnaryAuthInterceptor(secret string) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, request any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		err := authenticate(ctx, secret)
		if err != nil {
			return nil, err
		}
		return handler(ctx, request)
	}
}

func StreamAuthInterceptor(secret string) grpc.StreamServerInterceptor {
	return func(server any, stream grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
		err := authenticate(stream.Context(), secret)
		if err != nil {
			return err
		}
		return handler(server, stream)
	}
}

// authenticate is the Bearer secret check from sing-box daemon.NewServer (v1.14.0-rc.1).
// Keep in sync when upgrading sing-box.
func authenticate(ctx context.Context, secret string) error {
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
