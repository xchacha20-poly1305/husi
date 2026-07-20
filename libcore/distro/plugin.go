package distro

import (
	_ "libcore/combinedapi"
	"libcore/plugin/http"
	"libcore/plugin/juicity"
	"libcore/plugin/trusttunnel"
	"libcore/plugin/vless"

	"github.com/sagernet/sing-box/adapter/outbound"
)

func registerPluginsOutbound(registry *outbound.Registry) {
	http.RegisterOutbound(registry)
	juicity.RegisterOutbound(registry)
	vless.RegisterOutbound(registry)
	trusttunnel.RegisterOutbound(registry)
}
