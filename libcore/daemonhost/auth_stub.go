//go:build !linux && !darwin && !windows

package daemonhost

import (
	"context"

	E "github.com/sagernet/sing/common/exceptions"

	"google.golang.org/grpc"
)

func PlatformServerCredentials(registry *PeerRegistry, listenTCP bool) ([]grpc.ServerOption, error) {
	if listenTCP {
		return nil, nil
	}
	return nil, E.New("peer authentication is not supported on this platform")
}

func platformFallbackPeerIdentity(ctx context.Context) (PeerIdentity, error) {
	return PeerIdentity{}, E.New("peer authentication is not supported on this platform")
}
