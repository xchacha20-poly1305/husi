package libcore

import (
	"context"
	"time"

	"github.com/sagernet/sing-box/common/networkquality"
	"github.com/sagernet/sing-box/common/stun"
	"github.com/sagernet/sing-box/daemon"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pluginpool"
	"github.com/xchacha20-poly1305/husi/libcore/v2/urltest"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/status"
)

// noPluginLauncher serves hosts that cannot spawn children. It rejects specs
// instead of quietly testing an outbound whose plugin never started.
type noPluginLauncher struct{}

var _ pluginpool.Launcher = noPluginLauncher{}

func (noPluginLauncher) RunWithPlugins(specs []*husiv1.PluginProcessSpec, run func() (int32, error)) (int32, error) {
	if len(specs) > 0 {
		return -1, E.New("this host cannot spawn plugin processes")
	}
	return run()
}

// applicationService implements the husi.v1 application surface.
type applicationService struct {
	husiv1.UnimplementedApplicationServiceServer
	platformInterface PlatformInterface
	pluginLauncher    pluginpool.Launcher
}

var (
	_ husiv1.ApplicationServiceServer = (*applicationService)(nil)
	_ coresvc.ServiceRegistrar        = (*applicationService)(nil)
)

// NewApplicationService builds the application surface a core host serves.
// platformInterface backs the throwaway instance a standalone URL test spins up
// (Android only; desktop hosts pass nil), and launcher spawns that test's plugin
// children — a host without one refuses requests that carry plugin specs.
//
// Everything it returns is unexported on purpose: Kotlin reaches this surface
// over gRPC, so nothing here belongs in the Android binding.
func NewApplicationService(platformInterface PlatformInterface, launcher pluginpool.Launcher) coresvc.ServiceRegistrar {
	if launcher == nil {
		launcher = noPluginLauncher{}
	}
	return &applicationService{
		platformInterface: platformInterface,
		pluginLauncher:    launcher,
	}
}

func (s *applicationService) RegisterServices(server *grpc.Server, healthServer *health.Server) {
	husiv1.RegisterApplicationServiceServer(server, s)
	coresvc.ServingStatus(healthServer, husiv1.ApplicationService_ServiceDesc.ServiceName)
}

func (s *applicationService) CheckConfig(ctx context.Context, req *husiv1.CheckConfigRequest) (*husiv1.CheckConfigResponse, error) {
	if err := CheckConfig(req.GetConfig()); err != nil {
		return nil, rpcError(err, codes.InvalidArgument)
	}
	return &husiv1.CheckConfigResponse{}, nil
}

func (s *applicationService) GenerateSchema(ctx context.Context, req *husiv1.GenerateSchemaRequest) (*husiv1.GenerateSchemaResponse, error) {
	var (
		schema string
		err    error
	)
	switch kind := req.GetKind(); kind {
	case husiv1.SchemaKind_SCHEMA_KIND_CONFIG:
		schema, err = GenerateConfigSchema()
	case husiv1.SchemaKind_SCHEMA_KIND_OUTBOUND:
		schema, err = GenerateOutboundSchema()
	case husiv1.SchemaKind_SCHEMA_KIND_DNS_RULE:
		schema, err = GenerateDNSRuleSchema()
	default:
		err = E.New("unknown schema kind: ", kind.String())
	}
	if err != nil {
		return nil, rpcError(err, codes.InvalidArgument)
	}
	return &husiv1.GenerateSchemaResponse{Schema: schema}, nil
}

func (s *applicationService) StandaloneURLTest(ctx context.Context, req *husiv1.StandaloneURLTestRequest) (*husiv1.StandaloneURLTestResponse, error) {
	options := urltest.FlagsFromProto(req.GetOptions())
	latency, err := s.pluginLauncher.RunWithPlugins(req.GetPlugins(), func() (int32, error) {
		return standaloneURLTest(
			req.GetConfig(),
			req.GetOutboundTag(),
			req.GetLink(),
			req.GetTimeoutMs(),
			options,
			s.platformInterface,
		)
	})
	if err != nil {
		return nil, urltest.WrapError(err)
	}
	return &husiv1.StandaloneURLTestResponse{LatencyMs: latency}, nil
}

func (s *applicationService) GetCert(ctx context.Context, req *husiv1.GetCertRequest) (*husiv1.GetCertResponse, error) {
	pem, err := getCert(ctx, req.GetServer(), req.GetServerName(), req.GetMode(), req.GetSocksProxyUrl())
	if err != nil {
		return nil, rpcError(err, codes.InvalidArgument)
	}
	return &husiv1.GetCertResponse{Pem: pem}, nil
}

func (s *applicationService) StandaloneSTUNTest(
	req *husiv1.StandaloneSTUNTestRequest,
	stream husiv1.ApplicationService_StandaloneSTUNTestServer,
) error {
	result, err := stun.Run(stun.Options{
		Server:  req.GetServer(),
		Context: stream.Context(),
		OnProgress: func(progress stun.Progress) {
			_ = stream.Send(daemon.NewSTUNTestProgress(progress))
		},
	})
	if err != nil {
		return streamError(stream.Context(), stream.Send(&daemon.STUNTestProgress{
			IsFinal: true,
			Error:   err.Error(),
		}))
	}
	return streamError(stream.Context(), stream.Send(daemon.NewSTUNTestResult(result)))
}

func (s *applicationService) StandaloneNetworkQualityTest(
	req *husiv1.StandaloneNetworkQualityTestRequest,
	stream husiv1.ApplicationService_StandaloneNetworkQualityTestServer,
) error {
	client := networkquality.NewHTTPClient(nil)
	defer client.CloseIdleConnections()

	measurementClientFactory, err := networkquality.NewOptionalHTTP3Factory(nil, req.GetHttp3())
	if err != nil {
		return rpcError(err, codes.InvalidArgument)
	}

	result, err := networkquality.Run(networkquality.Options{
		ConfigURL:            req.GetConfigUrl(),
		HTTPClient:           client,
		NewMeasurementClient: measurementClientFactory,
		Serial:               req.GetSerial(),
		MaxRuntime:           time.Duration(req.GetMaxRuntimeSeconds()) * time.Second,
		Context:              stream.Context(),
		OnProgress: func(progress networkquality.Progress) {
			_ = stream.Send(daemon.NewNetworkQualityTestProgress(progress))
		},
	})
	if err != nil {
		return streamError(stream.Context(), stream.Send(&daemon.NetworkQualityTestProgress{
			IsFinal: true,
			Error:   err.Error(),
		}))
	}
	return streamError(stream.Context(), stream.Send(daemon.NewNetworkQualityTestResult(result)))
}

// rpcError keeps a status an implementation already chose, and labels a plain
// error with fallback.
func rpcError(err error, fallback codes.Code) error {
	if _, loaded := status.FromError(err); loaded {
		return err
	}
	return status.Error(fallback, err.Error())
}

// streamError reports a cancelled or expired stream as such: the client hanging
// up is not an internal failure.
func streamError(ctx context.Context, err error) error {
	if err == nil {
		return nil
	}
	if ctx.Err() != nil {
		return status.FromContextError(ctx.Err()).Err()
	}
	return rpcError(err, codes.Unknown)
}
