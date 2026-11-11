//go:build !linux || android

package daemonhost

import (
	"context"
	"errors"

	"github.com/sagernet/sing-box/adapter"
)

const supportsNetworkManagerTun = false

func newNetworkManagerPlatform(_ context.Context, _ func()) (adapter.PlatformInterface, error) {
	return nil, errors.ErrUnsupported
}
