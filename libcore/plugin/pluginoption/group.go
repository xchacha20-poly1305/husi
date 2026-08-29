package pluginoption

import (
	"github.com/sagernet/sing/common/json/badoption"
)

type BalancerOutboundOptions struct {
	Outbounds []string           `json:"outbounds" reference:"outbound"`
	Interval  badoption.Duration `json:"interval,omitempty"`
}
