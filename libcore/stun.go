package libcore

import (
	"context"
	"fmt"
	"net"
	"strings"

	"libcore/stun"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"
)

func StunTest(server, proxy, softwareName string) string {
	// note: this library doesn't support stun1.l.google.com:19302

	var packetConn net.PacketConn
	if proxy == "" {
		var err error
		packetConn, err = net.ListenUDP(N.NetworkUDP, nil)
		if err != nil {
			return E.Cause(err, "failed to create packet conn").Error()
		}
	} else {
		dialer, err := socks.NewClientFromURL(new(N.DefaultDialer), proxy)
		if err != nil {
			return E.Cause(err, "failed to create proxy dialer").Error()
		}
		packetConn, err = dialer.ListenPacket(context.Background(), M.ParseSocksaddr(server))
		if err != nil {
			return E.Cause(err, "create packet conn via proxy").Error()
		}
	}

	var resultBuilder strings.Builder

	// Old NAT Type Test
	client := stun.NewClientWithConnection(packetConn).SetServerAddr(server).SetSoftwareName(softwareName)
	client.SetLogLevel(log.LevelTrace)
	nat, host, err, fakeFullCone := client.Discover()
	if err != nil {
		_, _ = fmt.Fprintf(&resultBuilder, "Discover Error: %v\n", err)
	}

	if fakeFullCone {
		_, _ = resultBuilder.WriteString("Fake fullcone (no endpoint IP change) detected!!")
	}

	if host != nil {
		_, _ = fmt.Fprintf(&resultBuilder, "NAT Type: %s\n", nat)
		_, _ = fmt.Fprintf(&resultBuilder, "External IP Family: %d\n", host.Family())
		_, _ = fmt.Fprintf(&resultBuilder, "External IP: %s\n", host.IP())
		_, _ = fmt.Fprintf(&resultBuilder, "External Port: %d\n", host.Port())
	}

	// New NAT Test

	natBehavior, err := client.BehaviorTest()
	if err != nil {
		_, _ = fmt.Fprintf(&resultBuilder, "Behavior Test Error: %v\n", err)
	}

	if natBehavior != nil {
		_, _ = fmt.Fprintf(&resultBuilder, "Mapping Behavior: %s\n", natBehavior.MappingType.String())
		_, _ = fmt.Fprintf(&resultBuilder, "Filtering Behavior: %s\n", natBehavior.FilteringType.String())
		_, _ = fmt.Fprintf(&resultBuilder, "Normal NAT Type: %s\n", natBehavior.NormalType())
	}

	return resultBuilder.String()
}
