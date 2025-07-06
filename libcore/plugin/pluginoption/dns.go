package pluginoption

import (
	"github.com/sagernet/sing-box/option"
)

type RemoteTCPDNSServerOptions struct {
	option.RemoteDNSServerOptions
	Reuse bool `json:"reuse,omitempty"`
}
