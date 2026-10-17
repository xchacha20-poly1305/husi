//go:build with_tailscale

package distro

import (
	"github.com/sagernet/sing-box/adapter/endpoint"
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing-box/protocol/tailscale"
)

func registerTailscaleEndpoint(registry *endpoint.Registry) {
	tailscale.RegisterEndpoint(registry)
}

func registerTailcatOutbound(registry *outbound.Registry) {
	tailscale.RegisterTailcatOutbound(registry)
}

func registerTailscaleDNSTransport(registry *dns.TransportRegistry) {
	tailscale.RegistryTransport(registry)
}
