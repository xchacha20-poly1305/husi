//go:build android

package libcore

import (
	"context"

	"libcore/plugin/protect"

	"github.com/sagernet/sing-box/adapter"
	E "github.com/sagernet/sing/common/exceptions"
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
	service.MustRegister[protect.Protector](ctx, protect.ProtectorFunc(func(fileDescriptor int) error {
		if !platformInterface.AutoDetectInterfaceControl(int32(fileDescriptor)) {
			return E.New("platform refused to protect file descriptor ", fileDescriptor)
		}
		return nil
	}))
}
