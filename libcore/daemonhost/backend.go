package daemonhost

import (
	"github.com/xchacha20-poly1305/husi/libcore/v2"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pluginpool"
	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/health/grpc_health_v1"
)

type pooledBackend struct {
	libcore.HostBackend
	workingDir string
	credential pluginpool.ProcessCredentialFunc
}

func (b *pooledBackend) StandaloneURLTest(config, outboundTag, link string, timeoutMs int32, options uint8, plugins []*husiv1.PluginProcessSpec) (int32, error) {
	return pluginpool.RunWithPlugins(b.workingDir, plugins, b.credential, func() (int32, error) {
		return libcore.StandaloneURLTest(config, outboundTag, link, timeoutMs, options, nil)
	})
}

func registerDaemonExtraServices(server *grpc.Server, healthServer *health.Server, svc husiv1.DaemonServiceServer) {
	husiv1.RegisterDaemonServiceServer(server, svc)
	healthServer.SetServingStatus(husiv1.DaemonService_ServiceDesc.ServiceName, grpc_health_v1.HealthCheckResponse_SERVING)
}

func (s *sessionDaemonService) RegisterExtraServices(server *grpc.Server, healthServer *health.Server) {
	registerDaemonExtraServices(server, healthServer, s)
}

func (s *daemonDaemonService) RegisterExtraServices(server *grpc.Server, healthServer *health.Server) {
	registerDaemonExtraServices(server, healthServer, s)
}
