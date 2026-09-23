package pluginoption

import (
	"errors"

	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/schema"
)

type FinalmaskRaw []byte

func (m FinalmaskRaw) MarshalJSON() ([]byte, error) {
	if m == nil {
		return []byte("null"), nil
	}
	return m, nil
}

func (m *FinalmaskRaw) UnmarshalJSON(data []byte) error {
	if m == nil {
		return errors.New("FinalmaskRaw: UnmarshalJSON on nil pointer")
	}
	*m = append((*m)[0:0], data...)
	return nil
}

func (m FinalmaskRaw) DescribeSchema(builder schema.Builder) (*schema.Node, error) {
	return &schema.Node{Type: "object"}, nil
}

type VLESSOutboundOptions struct {
	option.VLESSOutboundOptions
	Encryption string       `json:"encryption,omitempty" examples:"none"`
	Finalmask  FinalmaskRaw `json:"finalmask,omitempty"`
}
