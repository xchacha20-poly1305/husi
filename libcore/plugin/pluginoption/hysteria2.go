package pluginoption

import (
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json/badoption"
)

type Hysteria2OutboundOptions struct {
	option.Hysteria2OutboundOptions
	MTlsCert badoption.Listable[string] `json:"m_tls_cert,omitempty"`
	MTlsKey  badoption.Listable[string] `json:"m_tls_key"`
}
