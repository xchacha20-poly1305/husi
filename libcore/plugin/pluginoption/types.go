package pluginoption

import (
	"strings"

	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
	N "github.com/sagernet/sing/common/network"
)

type NetworkListWithICMP string

func (v *NetworkListWithICMP) UnmarshalJSON(content []byte) error {
	var networkList []string
	err := json.Unmarshal(content, &networkList)
	if err != nil {
		var networkItem string
		err = json.Unmarshal(content, &networkItem)
		if err != nil {
			return err
		}
		networkList = []string{networkItem}
	}
	for _, networkName := range networkList {
		switch networkName {
		case N.NetworkTCP, N.NetworkUDP, N.NetworkICMP:
		default:
			return E.New("unknown network: " + networkName)
		}
	}
	*v = NetworkListWithICMP(strings.Join(networkList, "\n"))
	return nil
}

func (v NetworkListWithICMP) Build() []string {
	if v == "" {
		return []string{N.NetworkTCP, N.NetworkUDP, N.NetworkICMP}
	}
	return strings.Split(string(v), "\n")
}
