package distro

import (
	"github.com/sagernet/sing-box/adapter/outbound"

	"libcore/plugin/anytls"
)

func registerPluginsOutbound(registry *outbound.Registry) {
	anytls.RegisterOutbound(registry)
}
