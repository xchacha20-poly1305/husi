//go:build with_tailscale

package distro

import (
	"github.com/sagernet/sing-box/adapter/endpoint"
	"github.com/sagernet/sing-box/protocol/tailscale"
)

func registerTailscaleEndpoint(registry *endpoint.Registry) {
	tailscale.RegisterEndpoint(registry)
}
