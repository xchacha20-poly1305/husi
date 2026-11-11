package daemonhost

import (
	"context"

	"github.com/sagernet/sing-box/adapter"

	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	husiv1 "github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func validateServiceOptions(options *husiv1.ServiceOptions) error {
	switch options.GetTunBackend() {
	case husiv1.TunBackend_TUN_BACKEND_NATIVE:
		return nil
	case husiv1.TunBackend_TUN_BACKEND_NETWORK_MANAGER:
		if supportsNetworkManagerTun {
			return nil
		}
		return status.Error(codes.Unimplemented, "NetworkManager TUN is only available on Linux")
	default:
		return status.Error(codes.InvalidArgument, "unknown TUN backend")
	}
}

func startServiceWithOptions(ctx context.Context, host *coresvc.Host, config string, options *husiv1.ServiceOptions, onPlatformTunLost func(adapter.PlatformInterface)) error {
	if err := validateServiceOptions(options); err != nil {
		return err
	}
	if options.GetTunBackend() != husiv1.TunBackend_TUN_BACKEND_NETWORK_MANAGER {
		return host.StartOrReload(ctx, config)
	}
	var platform adapter.PlatformInterface
	platform, err := newNetworkManagerPlatform(host.InstanceContext(), func() {
		// The callback can run while the interface is locked; service teardown must
		// run after it returns. The service checks identity under its lifecycle lock.
		go onPlatformTunLost(platform)
	})
	if err != nil {
		return err
	}
	return host.StartOrReloadWithPlatform(ctx, config, platform)
}
