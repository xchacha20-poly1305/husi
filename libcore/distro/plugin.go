package distro

import (
	"github.com/sagernet/sing-box/adapter/outbound"

	_ "github.com/xchacha20-poly1305/husi/libcore/v2/combinedapi"
	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/http"
	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/juicity"
	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/trusttunnel"
	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/vless"
)

func registerPluginsOutbound(registry *outbound.Registry) {
	http.RegisterOutbound(registry)
	juicity.RegisterOutbound(registry)
	vless.RegisterOutbound(registry)
	trusttunnel.RegisterOutbound(registry)
}
