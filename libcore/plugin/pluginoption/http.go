package pluginoption

import (
	"github.com/sagernet/sing-box/option"
)

type HTTPOutboundOptions struct {
	option.HTTPOutboundOptions
	UDPOverTCP *option.UDPOverTCPOptions `json:"udp_over_tcp,omitempty"`
}
