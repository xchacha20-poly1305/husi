//go:build !android

package libcore

import (
	"context"
)

func registerPlatformInterface(ctx context.Context, platformInterface PlatformInterface, forTest bool) {
}
