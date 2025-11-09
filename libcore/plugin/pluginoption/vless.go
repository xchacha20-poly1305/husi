package pluginoption

import (
	"github.com/sagernet/sing-box/option"
)

type VLESSOutboundOptions struct {
	option.VLESSOutboundOptions
	Encryption string `json:"encryption,omitempty"`
}
