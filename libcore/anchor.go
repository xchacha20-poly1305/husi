package libcore

import (
	"net"
	"net/netip"
	"os"
	"regexp"
	"strings"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/anchor"
	"github.com/xchacha20-poly1305/anchor/anchorservice"
)

func publicMixedPort(inbounds []option.Inbound) (socksPort uint16) {
	for _, inbound := range inbounds {
		switch inbound.Type {
		case C.TypeMixed:
			mixedOption := inbound.Options.(*option.HTTPMixedInboundOptions)
			if mixedOption.Listen.Build(netip.Addr{}).IsLoopback() {
				break
			}
			return mixedOption.ListenPort
		}
	}
	return 0
}

func (b *BoxInstance) createAnchor(socksPort uint16) (*anchorservice.Anchor, error) {
	deviceName, _ := os.Hostname()

	ssids := strings.Split(b.platformInterface.GetDataStoreString("anchorSSID"), "\n")
	ssidRules := make([]*regexp.Regexp, 0, len(ssids))
	for _, ssid := range ssids {
		regex, err := regexp.Compile(ssid)
		if err != nil {
			return nil, E.Cause(err, "create ssid rule")
		}
		ssidRules = append(ssidRules, regex)
	}
	anchorResponse := &anchor.Response{
		Version:    anchor.Version,
		DeviceName: deviceName,
		SocksPort:  socksPort,
	}
	return anchorservice.New(b.ctx, log.StdLogger(), &net.UDPAddr{
		IP:   net.IPv4zero,
		Port: anchor.Port,
	}, anchorResponse, func(_ net.Addr, _ string) bool {
		return b.shouldRejectAnchorRequest(ssidRules)
	}), nil
}

func (b *BoxInstance) shouldRejectAnchorRequest(rules []*regexp.Regexp) bool {
	networkManager := b.Network()
	switch networkManager.DefaultNetworkInterface().Type {
	// Just allow connections from trusted Wi-Fi
	case C.InterfaceTypeWIFI:
		ssid := networkManager.WIFIState().SSID
		if common.Any(rules, func(it *regexp.Regexp) bool {
			return it.MatchString(ssid)
		}) {
			return false
		}
		return true
	case C.InterfaceTypeEthernet:
		return false
	default:
		return true
	}
}
