//go:build !with_naive_outbound

package distro

import (
	"github.com/sagernet/sing-box/adapter/outbound"
)

func registerNaiveOutbound(registry *outbound.Registry) {}
