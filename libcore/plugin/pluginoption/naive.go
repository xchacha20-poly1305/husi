package pluginoption

import (
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json/badoption"
)

type NaiveOutboundOptions struct {
	option.DialerOptions
	option.ServerOptions
	Username            string               `json:"username,omitempty"`
	Password            string               `json:"password,omitempty"`
	InsecureConcurrency int                  `json:"insecure_concurrency,omitempty"`
	ExtraHeaders        badoption.HTTPHeader `json:"extra_headers,omitempty"`
	option.OutboundTLSOptionsContainer
}
