package daemonhost

import (
	"github.com/xchacha20-poly1305/husi/libcore/v2"
	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pluginpool"
	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
)

// pluginLauncher runs the transient plugin children of a standalone URL test
// under the host's working directory. The daemon hands down credential so those
// children never inherit its privileges; a session host leaves it nil and stays
// as the user who started it.
type pluginLauncher struct {
	workingDir string
	credential pluginpool.ProcessCredentialFunc
}

var _ pluginpool.Launcher = pluginLauncher{}

func (l pluginLauncher) RunWithPlugins(specs []*husiv1.PluginProcessSpec, run func() (int32, error)) (int32, error) {
	return pluginpool.RunWithPlugins(l.workingDir, specs, l.credential, run)
}

func newApplicationService(workingDir string, credential pluginpool.ProcessCredentialFunc) coresvc.ServiceRegistrar {
	return libcore.NewApplicationService(nil, pluginLauncher{
		workingDir: workingDir,
		credential: credential,
	})
}

func registerDaemonService(server *grpc.Server, healthServer *health.Server, svc husiv1.DaemonServiceServer) {
	husiv1.RegisterDaemonServiceServer(server, svc)
	coresvc.ServingStatus(healthServer, husiv1.DaemonService_ServiceDesc.ServiceName)
}

func (s *sessionDaemonService) RegisterServices(server *grpc.Server, healthServer *health.Server) {
	registerDaemonService(server, healthServer, s)
}

func (s *daemonDaemonService) RegisterServices(server *grpc.Server, healthServer *health.Server) {
	registerDaemonService(server, healthServer, s)
}
