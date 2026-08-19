package distro

import (
	"github.com/sagernet/sing-box/adapter/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/anchor"
)

func registerAnchor(registry *service.Registry) {
	anchor.RegisterService(registry)
}
