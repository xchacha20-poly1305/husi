package distro

import (
	"libcore/plugin/http"
	"libcore/plugin/juicity"
	"libcore/plugin/plugindns"
	"libcore/plugin/vless"

	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/dns"
)

func registerPluginsOutbound(registry *outbound.Registry) {
	http.RegisterOutbound(registry)
	juicity.RegisterOutbound(registry)
	vless.RegisterOutbound(registry)
	registerNaiveOutbound(registry)
}

func registerPluginsDNSTransport(registry *dns.TransportRegistry) {
	plugindns.RegisterTCP(registry)
}
