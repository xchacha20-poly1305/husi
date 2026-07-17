//go:build with_openconnect

package distro

import (
	"github.com/sagernet/sing-box/adapter/endpoint"
	"github.com/sagernet/sing-box/protocol/openconnect"
)

func registerOpenConnectEndpoint(registry *endpoint.Registry) {
	openconnect.RegisterEndpoint(registry)
}
