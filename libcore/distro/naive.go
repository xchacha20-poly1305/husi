//go:build with_naive_outbound

package distro

import (
	"libcore/plugin/naive"

	"github.com/sagernet/sing-box/adapter/outbound"
)

func registerNaiveOutbound(registry *outbound.Registry) {
	naive.RegisterOutbound(registry)
}
