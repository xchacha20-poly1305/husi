package pluginoption

import (
	"github.com/sagernet/sing-box/option"
)

type TrustTunnelOutboundOptions struct {
	option.DialerOptions
	option.ServerOptions
	Username              string `json:"username,omitempty"`
	Password              string `json:"password,omitempty"`
	HealthCheck           bool   `json:"health_check,omitempty"`
	QUIC                  bool   `json:"quic,omitempty"`
	QUICCongestionControl string `json:"quic_congestion_control,omitempty"`
	option.OutboundTLSOptionsContainer
}
