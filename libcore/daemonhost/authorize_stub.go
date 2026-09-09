//go:build !linux

package daemonhost

import (
	"context"
)

func authorizeTakeOver(ctx context.Context, identity PeerIdentity) error {
	return nil
}
