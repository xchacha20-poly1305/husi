package pluginoption

import (
	"github.com/sagernet/sing-box/option"
)

type RemoteTCPDNSServerOptions struct {
	option.RemoteDNSServerOptions
	Reuse      bool `json:"reuse,omitempty"`
	Pipeline   bool `json:"pipeline,omitempty"`
	MaxQueries int  `json:"max_queries,omitempty"`
}

type RemoteTLSDNSServerOptions struct {
	option.RemoteDNSServerOptions
	option.OutboundTLSOptionsContainer
	Pipeline   bool `json:"pipeline,omitempty"`
	MaxQueries int  `json:"max_queries,omitempty"`
}
