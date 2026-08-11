//go:build android

package libcore

import (
	"context"

	"libcore/protect"

	"github.com/sagernet/sing-box/log"
)

func startProtectService(s *Service) {
	if s.platformInterface == nil {
		return
	}
	protectService, err := protect.New(
		log.ContextWithNewID(context.Background()),
		logFactory.NewLogger("protect"),
		ProtectPath,
		func(fd int) error {
			_ = s.platformInterface.AutoDetectInterfaceControl(int32(fd))
			return nil
		},
	)
	if err != nil {
		log.Warn("create protect service: ", err)
		return
	}
	s.protect = protectService
	_ = s.protect.Start()
}
