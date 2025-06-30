package distro

import (
	"github.com/sagernet/sing-box/adapter/outbound"

	"libcore/plugin/http"
)

func registerPluginsOutbound(registry *outbound.Registry) {
	http.RegisterOutbound(registry)
}
