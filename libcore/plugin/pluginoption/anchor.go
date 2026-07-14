package pluginoption

import (
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json/badoption"
)

type AnchorServiceOptions struct {
	option.ListenOptions
	DNSPort      uint16                     `json:"dns_port,omitempty"`
	DeviceName   string                     `json:"device_name,omitempty"`
	SocksPort    uint16                     `json:"socks_port,omitempty"`
	AllowedSSIDs badoption.Listable[string] `json:"allowed_ssids,omitempty"`
}
