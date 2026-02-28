//go:build with_naive_outbound

package distro

import (
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/protocol/naive"
)

func registerNaiveOutbound(registry *outbound.Registry) {
	naive.RegisterOutbound(registry)
}
