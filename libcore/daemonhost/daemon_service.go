package daemonhost

import (
	"context"
	"sync"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pluginpool"
	"github.com/xchacha20-poly1305/husi/libcore/v2/rootcerts"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// sessionDaemonService is the session-mode subset of husi.v1.DaemonService.
// Daemon-only RPCs (claim, takeover, boot) return Unimplemented.
type sessionDaemonService struct {
	husiv1.UnimplementedDaemonServiceServer

	host       *coresvc.Host
	workingDir string
	version    string

	access   sync.Mutex
	plugins  *pluginpool.PluginPool
	metadata *husiv1.ClientMetadata
}

func (s *sessionDaemonService) GetDaemonInfo(context.Context, *husiv1.GetDaemonInfoRequest) (*husiv1.GetDaemonInfoResponse, error) {
	return &husiv1.GetDaemonInfoResponse{
		Version:    s.version,
		ApiVersion: daemon.APIVersion,
		Hosting:    husiv1.Hosting_HOSTING_SESSION,
		// Session is single-user by nature; ownership fields stay empty.
		Ownership: &husiv1.Ownership{},
		Capabilities: &husiv1.Capabilities{
			Tun:               false,
			NetworkManagerTun: supportsNetworkManagerTun,
			StartAtBoot:       false,
		},
		StartAtBoot: false,
	}, nil
}

func (s *sessionDaemonService) StartService(ctx context.Context, req *husiv1.StartServiceRequest) (*husiv1.StartServiceResponse, error) {
	if req == nil {
		return nil, status.Error(codes.InvalidArgument, "missing start service request")
	}
	config := req.GetConfig()
	if config == "" {
		return nil, status.Error(codes.InvalidArgument, "missing config")
	}

	err := validateServiceOptions(req.GetOptions())
	if err != nil {
		return nil, err
	}
	s.access.Lock()
	defer s.access.Unlock()

	err = s.stopLocked()
	if err != nil {
		return nil, status.Error(codes.Internal, err.Error())
	}

	applyRootCertificates(req.GetOptions())
	err = s.plugins.StartAll(req.GetPlugins())
	if err != nil {
		return nil, status.Error(codes.Internal, E.Cause(err, "start plugins").Error())
	}

	err = startServiceWithOptions(ctx, s.host, config, req.GetOptions(), s.handleTunnelLost)
	if err != nil {
		_ = s.plugins.Close()
		return nil, status.Error(codes.Internal, E.Cause(err, "start service").Error())
	}

	s.metadata = cloneClientMetadata(req.GetClientMetadata())
	return &husiv1.StartServiceResponse{}, nil
}

func (s *sessionDaemonService) StopService(context.Context, *husiv1.StopServiceRequest) (*husiv1.StopServiceResponse, error) {
	s.access.Lock()
	defer s.access.Unlock()
	if err := s.stopLocked(); err != nil {
		return nil, status.Error(codes.Internal, err.Error())
	}
	return &husiv1.StopServiceResponse{}, nil
}

func (s *sessionDaemonService) GetClientMetadata(context.Context, *husiv1.GetClientMetadataRequest) (*husiv1.GetClientMetadataResponse, error) {
	s.access.Lock()
	defer s.access.Unlock()
	return &husiv1.GetClientMetadataResponse{
		ClientMetadata: cloneClientMetadata(s.metadata),
	}, nil
}

func (s *sessionDaemonService) ClaimService(context.Context, *husiv1.ClaimServiceRequest) (*husiv1.ClaimServiceResponse, error) {
	return nil, status.Error(codes.Unimplemented, "claim service is only available in daemon mode")
}

func (s *sessionDaemonService) TakeOverService(context.Context, *husiv1.TakeOverServiceRequest) (*husiv1.TakeOverServiceResponse, error) {
	return nil, status.Error(codes.Unimplemented, "take over service is only available in daemon mode")
}

func (s *sessionDaemonService) SetStartAtBoot(context.Context, *husiv1.SetStartAtBootRequest) (*husiv1.SetStartAtBootResponse, error) {
	return nil, status.Error(codes.Unimplemented, "start at boot is only available in daemon mode")
}

// stopLocked stops plugins and the box instance and clears client metadata.
// Caller must hold s.access. A fresh plugin pool is installed so the next
// StartService can spawn children again.
func (s *sessionDaemonService) stopLocked() error {
	var errs []error
	if s.plugins != nil {
		if err := s.plugins.Close(); err != nil {
			errs = append(errs, E.Cause(err, "stop plugins"))
		}
	}
	s.plugins = pluginpool.NewPluginPool(s.workingDir, s.handlePluginFatal)
	if s.host != nil {
		if err := s.host.CloseService(C.FatalStopTimeout); err != nil {
			errs = append(errs, E.Cause(err, "stop service"))
		}
	}
	s.metadata = nil
	return E.Errors(errs...)
}

// handlePluginFatal stops the running service after a supervised plugin dies
// too quickly. StartedService transitions to FATAL / idle via CloseService.
func (s *sessionDaemonService) handlePluginFatal(err error) {
	log.Error("plugin fatal, stopping service: ", err)
	s.access.Lock()
	defer s.access.Unlock()
	if stopErr := s.stopLocked(); stopErr != nil {
		log.Warn("stop after plugin fatal: ", stopErr)
	}
}

// applyRootCertificates installs the roots options selects for this process.
// UNSPECIFIED, as sent by a client that predates the option, keeps the current ones.
func applyRootCertificates(options *husiv1.ServiceOptions) {
	store, loaded := rootcerts.StoreFromProto(options.GetRootCertificates())
	if !loaded {
		return
	}
	rootcerts.Install(rootcerts.Load(store))
}

func cloneClientMetadata(src *husiv1.ClientMetadata) *husiv1.ClientMetadata {
	if src == nil {
		return nil
	}
	return &husiv1.ClientMetadata{
		ProfileId:   src.GetProfileId(),
		ProfileName: src.GetProfileName(),
	}
}

func (s *sessionDaemonService) handleTunnelLost(platform adapter.PlatformInterface) {
	s.access.Lock()
	defer s.access.Unlock()
	if s.host.UsesPlatform(platform) {
		log.Warn("NetworkManager connection lost, stopping service")
		err := s.stopLocked()
		if err != nil {
			log.Warn("stop after interface loss: ", err)
		}
	}
}
