package pluginoption

import "github.com/sagernet/sing-box/option"

type JuicityOutboundOptions struct {
	option.DialerOptions
	option.ServerOptions
	UUID     string `json:"uuid,omitempty"`
	Password string `json:"password,omitempty"`
	option.OutboundTLSOptionsContainer
	PinCertSha256 string `json:"pin_cert_sha256,omitempty"`
}
