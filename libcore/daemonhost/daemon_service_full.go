package daemonhost

import (
	"context"
	"sync"
	"syscall"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pluginpool"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type daemonDaemonService struct {
	husiv1.UnimplementedDaemonServiceServer

	host       *coresvc.Host
	workingDir string
	version    string
	owner      *OwnerStore

	access   sync.Mutex
	plugins  *pluginpool.PluginPool
	metadata *husiv1.ClientMetadata
	// lastCredentialAttr holds the Windows token (if any) for the active pool.
	lastCredentialAttr *syscall.SysProcAttr
}

func newDaemonDaemonService(workingDir, version string, owner *OwnerStore) *daemonDaemonService {
	s := &daemonDaemonService{
		workingDir: workingDir,
		version:    version,
		owner:      owner,
	}
	s.plugins = pluginpool.NewPluginPool(workingDir, s.handlePluginFatal)
	s.plugins.SetProcessCredential(s.pluginCredentials)
	return s
}

func (s *daemonDaemonService) pluginCredentials() (*syscall.SysProcAttr, error) {
	identity := s.owner.Owner()
	if identity == nil {
		return nil, E.New("no owner for plugin credentials")
	}
	return ProcessCredentialForOwner(*identity)
}

func (s *daemonDaemonService) GetDaemonInfo(ctx context.Context, _ *husiv1.GetDaemonInfoRequest) (*husiv1.GetDaemonInfoResponse, error) {
	var caller *PeerIdentity
	if identity, err := PeerIdentityFromContext(ctx); err == nil {
		caller = &identity
	}
	return &husiv1.GetDaemonInfoResponse{
		Version:    s.version,
		ApiVersion: daemon.APIVersion,
		Hosting:    husiv1.Hosting_HOSTING_DAEMON,
		Ownership:  s.owner.OwnershipInfo(caller),
		Capabilities: &husiv1.Capabilities{
			Tun:         true,
			StartAtBoot: true,
		},
		StartAtBoot: StartAtBoot(s.workingDir),
	}, nil
}

func (s *daemonDaemonService) ClaimService(ctx context.Context, _ *husiv1.ClaimServiceRequest) (*husiv1.ClaimServiceResponse, error) {
	identity, err := PeerIdentityFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, err.Error())
	}
	if err := s.owner.Claim(identity); err != nil {
		return nil, err
	}
	if err := SaveOwnerState(s.workingDir, identity); err != nil {
		log.Warn("save owner state: ", err)
	}
	return &husiv1.ClaimServiceResponse{}, nil
}

func (s *daemonDaemonService) TakeOverService(ctx context.Context, _ *husiv1.TakeOverServiceRequest) (*husiv1.TakeOverServiceResponse, error) {
	identity, err := PeerIdentityFromContext(ctx)
	if err != nil {
		return nil, status.Error(codes.Unauthenticated, err.Error())
	}
	if err := s.owner.TakeOver(identity); err != nil {
		return nil, err
	}
	if err := SaveOwnerState(s.workingDir, identity); err != nil {
		log.Warn("save owner state: ", err)
	}
	// Refresh plugin credential token for the new owner (Windows session).
	s.access.Lock()
	s.refreshPluginCredentialLocked()
	s.access.Unlock()
	return &husiv1.TakeOverServiceResponse{}, nil
}

func (s *daemonDaemonService) StartService(ctx context.Context, req *husiv1.StartServiceRequest) (*husiv1.StartServiceResponse, error) {
	if req == nil {
		return nil, status.Error(codes.InvalidArgument, "missing start service request")
	}
	config := req.GetConfig()
	if config == "" {
		return nil, status.Error(codes.InvalidArgument, "missing config")
	}
	// Owner check is also enforced by the authorize interceptor; re-check for clarity.
	if identity, err := PeerIdentityFromContext(ctx); err == nil {
		if !s.owner.IsOwner(identity) {
			// Auto-claim if unclaimed (first StartService without explicit Claim).
			if !s.owner.HasOwner() {
				if claimErr := s.owner.Claim(identity); claimErr != nil {
					return nil, claimErr
				}
				_ = SaveOwnerState(s.workingDir, identity)
			} else {
				return nil, status.Error(codes.PermissionDenied, "the service is owned by another user")
			}
		}
	}

	s.access.Lock()
	defer s.access.Unlock()

	if err := s.stopLocked(false); err != nil {
		return nil, status.Error(codes.Internal, err.Error())
	}
	s.refreshPluginCredentialLocked()

	snapshot := &Snapshot{
		Config:         config,
		Plugins:        clonePluginSpecs(req.GetPlugins()),
		ClientMetadata: cloneClientMetadata(req.GetClientMetadata()),
		Options:        req.GetOptions(),
	}
	if err := SaveSnapshot(s.workingDir, snapshot); err != nil {
		return nil, status.Error(codes.Internal, E.Cause(err, "save snapshot").Error())
	}
	if err := SetWasRunning(s.workingDir, true); err != nil {
		return nil, status.Error(codes.Internal, E.Cause(err, "set was_running").Error())
	}

	if err := s.plugins.StartAll(req.GetPlugins()); err != nil {
		_ = SetWasRunning(s.workingDir, false)
		return nil, status.Error(codes.Internal, E.Cause(err, "start plugins").Error())
	}

	if err := s.host.StartOrReload(ctx, config); err != nil {
		_ = s.plugins.Close()
		s.plugins = pluginpool.NewPluginPool(s.workingDir, s.handlePluginFatal)
		s.plugins.SetProcessCredential(s.pluginCredentials)
		_ = SetWasRunning(s.workingDir, false)
		return nil, status.Error(codes.Internal, E.Cause(err, "start service").Error())
	}

	s.metadata = cloneClientMetadata(req.GetClientMetadata())
	return &husiv1.StartServiceResponse{}, nil
}

func (s *daemonDaemonService) StopService(context.Context, *husiv1.StopServiceRequest) (*husiv1.StopServiceResponse, error) {
	s.access.Lock()
	defer s.access.Unlock()
	if err := s.stopLocked(true); err != nil {
		return nil, status.Error(codes.Internal, err.Error())
	}
	return &husiv1.StopServiceResponse{}, nil
}

func (s *daemonDaemonService) GetClientMetadata(context.Context, *husiv1.GetClientMetadataRequest) (*husiv1.GetClientMetadataResponse, error) {
	s.access.Lock()
	defer s.access.Unlock()
	return &husiv1.GetClientMetadataResponse{
		ClientMetadata: cloneClientMetadata(s.metadata),
	}, nil
}

func (s *daemonDaemonService) SetStartAtBoot(_ context.Context, req *husiv1.SetStartAtBootRequest) (*husiv1.SetStartAtBootResponse, error) {
	if req == nil {
		return nil, status.Error(codes.InvalidArgument, "missing set start at boot request")
	}
	if err := SetStartAtBoot(s.workingDir, req.GetEnabled()); err != nil {
		return nil, status.Error(codes.Internal, err.Error())
	}
	return &husiv1.SetStartAtBootResponse{}, nil
}

// restore starts from a saved snapshot without owner checks (boot path).
func (s *daemonDaemonService) restore(ctx context.Context, snapshot *Snapshot) error {
	if snapshot == nil || snapshot.Config == "" {
		return E.New("empty snapshot")
	}
	s.access.Lock()
	defer s.access.Unlock()

	if err := s.stopLocked(false); err != nil {
		return err
	}
	s.refreshPluginCredentialLocked()

	if err := s.plugins.StartAll(snapshot.Plugins); err != nil {
		return E.Cause(err, "restore plugins")
	}
	if err := s.host.StartOrReload(ctx, snapshot.Config); err != nil {
		_ = s.plugins.Close()
		s.plugins = pluginpool.NewPluginPool(s.workingDir, s.handlePluginFatal)
		s.plugins.SetProcessCredential(s.pluginCredentials)
		return E.Cause(err, "restore service")
	}
	s.metadata = cloneClientMetadata(snapshot.ClientMetadata)
	return nil
}

// stopLocked stops plugins and the box instance.
// clearPersistence also clears was_running and the snapshot when true.
func (s *daemonDaemonService) stopLocked(clearPersistence bool) error {
	var errs []error
	if s.plugins != nil {
		if err := s.plugins.Close(); err != nil {
			errs = append(errs, E.Cause(err, "stop plugins"))
		}
	}
	closeProcessCredential(s.lastCredentialAttr)
	s.lastCredentialAttr = nil

	s.plugins = pluginpool.NewPluginPool(s.workingDir, s.handlePluginFatal)
	s.plugins.SetProcessCredential(s.pluginCredentials)

	if s.host != nil {
		if err := s.host.CloseService(C.FatalStopTimeout); err != nil {
			errs = append(errs, E.Cause(err, "stop service"))
		}
	}
	s.metadata = nil
	if clearPersistence {
		if err := SetWasRunning(s.workingDir, false); err != nil {
			errs = append(errs, err)
		}
		if err := ClearSnapshot(s.workingDir); err != nil {
			errs = append(errs, err)
		}
	}
	return E.Errors(errs...)
}

func (s *daemonDaemonService) refreshPluginCredentialLocked() {
	closeProcessCredential(s.lastCredentialAttr)
	s.lastCredentialAttr = nil
	// Pre-acquire Windows token so StartAll does not open it per-plugin; the
	// ProcessCredentialFunc still builds SysProcAttr each Start call.
}

func (s *daemonDaemonService) handlePluginFatal(err error) {
	log.Error("plugin fatal, stopping service: ", err)
	s.access.Lock()
	defer s.access.Unlock()
	if stopErr := s.stopLocked(true); stopErr != nil {
		log.Warn("stop after plugin fatal: ", stopErr)
	}
}

func clonePluginSpecs(src []*husiv1.PluginProcessSpec) []*husiv1.PluginProcessSpec {
	if len(src) == 0 {
		return nil
	}
	out := make([]*husiv1.PluginProcessSpec, 0, len(src))
	for _, spec := range src {
		if spec == nil {
			continue
		}
		cloned := &husiv1.PluginProcessSpec{
			Name:    spec.GetName(),
			Command: append([]string(nil), spec.GetCommand()...),
		}
		if env := spec.GetEnvironment(); len(env) > 0 {
			cloned.Environment = make(map[string]string, len(env))
			for k, v := range env {
				cloned.Environment[k] = v
			}
		}
		if files := spec.GetFiles(); len(files) > 0 {
			cloned.Files = make([]*husiv1.PluginFile, 0, len(files))
			for _, f := range files {
				if f == nil {
					continue
				}
				cloned.Files = append(cloned.Files, &husiv1.PluginFile{
					Name:    f.GetName(),
					Content: append([]byte(nil), f.GetContent()...),
				})
			}
		}
		out = append(out, cloned)
	}
	return out
}
