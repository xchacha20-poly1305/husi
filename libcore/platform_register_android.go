//go:build android

package libcore

import (
	"context"

	"github.com/sagernet/sing-box/adapter"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/protect"
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
	service.MustRegister[protect.Protector](ctx, protect.ProtectorFunc(func(fileDescriptor int) error {
		if !platformInterface.AutoDetectInterfaceControl(int32(fileDescriptor)) {
			return E.New("platform refused to protect file descriptor ", fileDescriptor)
		}
		return nil
	}))
}
