package pluginoption

import (
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json/badoption"
)

type AnyTLSOutboundOptions struct {
	option.DialerOptions
	option.ServerOptions
	option.OutboundTLSOptionsContainer
	Password                 string             `json:"password,omitempty"`
	IdleSessionCheckInterval badoption.Duration `json:"idle_session_check_interval,omitempty"`
	IdleSessionTimeout       badoption.Duration `json:"idle_session_timeout,omitempty"`
}
