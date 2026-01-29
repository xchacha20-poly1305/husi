package distro

import (
	"libcore/plugin/http"
	"libcore/plugin/juicity"
	"libcore/plugin/naive"
	"libcore/plugin/plugindns"
	"libcore/plugin/trusttunnel"
	_ "libcore/plugin/trusttunnel/quic"
	"libcore/plugin/vless"

	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/dns"
)

func registerPluginsOutbound(registry *outbound.Registry) {
	http.RegisterOutbound(registry)
	juicity.RegisterOutbound(registry)
	vless.RegisterOutbound(registry)
	naive.RegisterOutbound(registry)
	trusttunnel.RegisterOutbound(registry)
}

func registerPluginsDNSTransport(registry *dns.TransportRegistry) {
	plugindns.RegisterTCP(registry)
	plugindns.RegisterTLS(registry)
}
