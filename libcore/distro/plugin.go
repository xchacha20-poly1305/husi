package distro

import (
	"github.com/sagernet/sing-box/adapter/outbound"

	"libcore/plugin/anytls"
	"libcore/plugin/http"
)

func registerPluginsOutbound(registry *outbound.Registry) {
	anytls.RegisterOutbound(registry)
	http.RegisterOutbound(registry)
)
