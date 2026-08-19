package coresvc

import (
	"context"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/status"
)

type STUNTestSender interface {
	Send(response *husiv1.STUNTestResponse) error
}

type SpeedTestSender interface {
	Send(response *husiv1.SpeedTestResponse) error
}

type Backend interface {
	CheckConfig(config string) error
	GenerateSchema(kind husiv1.SchemaKind) (string, error)
	StandaloneURLTest(config, outboundTag, link string, timeoutMs int32, options uint8, plugins []*husiv1.PluginProcessSpec) (int32, error)
	GetCert(ctx context.Context, server, serverName string, mode husiv1.GetCertMode, socksProxyURL string) (string, error)
	STUNTest(ctx context.Context, server, socksProxyURL string, sender STUNTestSender) error
	SpeedTest(ctx context.Context, request *husiv1.SpeedTestRequest, sender SpeedTestSender) error
	BuildEnvironment() string
}

type ExtraServiceRegistrar interface {
	RegisterExtraServices(server *grpc.Server, healthServer *health.Server)
}

type UnimplementedBackend struct{}

func (UnimplementedBackend) CheckConfig(string) error {
	return status.Error(codes.Unimplemented, "CheckConfig is not configured")
}

func (UnimplementedBackend) GenerateSchema(husiv1.SchemaKind) (string, error) {
	return "", status.Error(codes.Unimplemented, "GenerateSchema is not configured")
}

func (UnimplementedBackend) StandaloneURLTest(string, string, string, int32, uint8, []*husiv1.PluginProcessSpec) (int32, error) {
	return 0, status.Error(codes.Unimplemented, "StandaloneURLTest is not configured")
}

func (UnimplementedBackend) GetCert(context.Context, string, string, husiv1.GetCertMode, string) (string, error) {
	return "", status.Error(codes.Unimplemented, "GetCert is not configured")
}

func (UnimplementedBackend) STUNTest(context.Context, string, string, STUNTestSender) error {
	return status.Error(codes.Unimplemented, "STUNTest is not configured")
}

func (UnimplementedBackend) SpeedTest(context.Context, *husiv1.SpeedTestRequest, SpeedTestSender) error {
	return status.Error(codes.Unimplemented, "SpeedTest is not configured")
}

func (UnimplementedBackend) BuildEnvironment() string {
	return ""
}

func backendError(err error, fallback codes.Code) error {
	if _, loaded := status.FromError(err); loaded {
		return err
	}
	return status.Error(fallback, err.Error())
}
