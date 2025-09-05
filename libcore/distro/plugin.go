package distro

import (
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/dns"

	"libcore/plugin/http"
	"libcore/plugin/juicity"
	"libcore/plugin/plugindns"
)

func registerPluginsOutbound(registry *outbound.Registry) {
	http.RegisterOutbound(registry)
	juicity.RegisterOutbound(registry)
}

func registerPluginsDNSTransport(registry *dns.TransportRegistry) {
	plugindns.RegisterTCP(registry)
}
