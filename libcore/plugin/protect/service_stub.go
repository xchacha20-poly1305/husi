//go:build !android

package protect

import (
	"context"

	"libcore/plugin/pluginoption"

	"github.com/sagernet/sing-box/adapter"
	boxService "github.com/sagernet/sing-box/adapter/service"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
)

func RegisterService(registry *boxService.Registry) {
	boxService.Register[pluginoption.ProtectServiceOptions](registry, pluginoption.TypeProtect, func(ctx context.Context, logger log.ContextLogger, tag string, options pluginoption.ProtectServiceOptions) (adapter.Service, error) {
		return nil, E.New("protect service is only available on Android")
	})
}
