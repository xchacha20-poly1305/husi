//go:build !with_naive_outbound

package naive

import "github.com/sagernet/sing-box/adapter/outbound"

func RegisterOutbound(registry *outbound.Registry) {
}
