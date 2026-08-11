//go:build android

package libcore

import (
	"context"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing/service"
)

func registerPlatformInterface(ctx context.Context, platformInterface PlatformInterface, forTest bool) {
	if platformInterface == nil {
		return
	}
	interfaceWrapper := &boxPlatformInterfaceWrapper{
		useProcFS: platformInterface.UseProcFS(),
		iif:       platformInterface,
		forTest:   forTest,
	}
	service.MustRegister[adapter.PlatformInterface](ctx, interfaceWrapper)
}
