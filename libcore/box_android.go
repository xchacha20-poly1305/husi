//go:build android

package libcore

import (
	"context"

	"libcore/protect"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/service"
)

func registerPlatformInterface(ctx context.Context, platformInterface PlatformInterface, forTest bool) {
	interfaceWrapper := &boxPlatformInterfaceWrapper{
		useProcFS: platformInterface.UseProcFS(),
		iif:       platformInterface,
		forTest:   forTest,
	}
	service.MustRegister[adapter.PlatformInterface](ctx, interfaceWrapper)
}

func buildProtectService(box *boxInstance, ctx context.Context, platformInterface PlatformInterface) {
	protectService, err := protect.New(log.ContextWithNewID(ctx), logFactory.NewLogger("protect"), ProtectPath, func(fd int) error {
		_ = platformInterface.AutoDetectInterfaceControl(int32(fd))
		return nil
	})
	if err != nil {
		log.WarnContext(ctx, "create protect service: ", err)
	}
	box.protect = protectService
}
