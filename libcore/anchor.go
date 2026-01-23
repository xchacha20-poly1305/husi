package libcore

import (
	"net"
	"net/netip"
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

func sharedPublicPort(inbounds []option.Inbound) (socksPort, dnsPort uint16) {
	for _, inbound := range inbounds {
		switch inbound.Type {
		case C.TypeMixed:
			mixedOption := inbound.Options.(*option.HTTPMixedInboundOptions)
			if mixedOption.Listen.Build(netip.Addr{}).IsLoopback() {
				// Not for public
				return 0, 0
			}
			socksPort = mixedOption.ListenPort
		case C.TypeDirect:
			directOption := inbound.Options.(*option.DirectInboundOptions)
			if directOption.OverridePort > 0 {
				dnsPort = directOption.OverridePort
			}
		}
	}
	return
}

func (b *boxInstance) createAnchor(socksPort, dnsPort uint16) (*anchorservice.Anchor, error) {
	ssid := b.platformInterface.AnchorSSID()
	if ssid == "" {
		// Not set any rule, unnecessary to start service.
		return nil, nil
	}
	ssids := strings.Split(ssid, "\n")
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
		DnsPort:    dnsPort,
		DeviceName: b.platformInterface.DeviceName(),
		SocksPort:  socksPort,
	}
	return anchorservice.New(
		log.ContextWithNewID(b.ctx),
		logFactory.NewLogger("anchor"),
		nil,
		anchorResponse, func(_ net.Addr, _ string) bool {
			return b.shouldRejectAnchorRequest(ssidRules)
		},
	), nil
}

func (b *boxInstance) shouldRejectAnchorRequest(rules []*regexp.Regexp) bool {
	networkManager := b.Network()
	switch networkManager.DefaultNetworkInterface().Type {
	case C.InterfaceTypeWIFI:
		// Only allow connections from trusted Wi-Fi
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
