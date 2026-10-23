//go:build android

package libcore

import (
	"net/netip"
	"syscall"

	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"

	"github.com/xchacha20-poly1305/husi/libcore/v2/procfs"
)

func FindConnectionOwnerUid(ipProtocol int32, sourceAddress string, sourcePort int32, destinationAddress string, destinationPort int32) (int32, error) {
	var network string
	switch ipProtocol {
	case syscall.IPPROTO_TCP:
		network = N.NetworkTCP
	case syscall.IPPROTO_UDP:
		network = N.NetworkUDP
	default:
		return 0, E.New("unknown protocol: ", ipProtocol)
	}
	sourceIP, err := netip.ParseAddr(sourceAddress)
	if err != nil {
		return 0, E.Cause(err, "parse source")
	}
	destinationIP, _ := netip.ParseAddr(destinationAddress)
	uid := procfs.ResolveSocketByProcSearch(
		network,
		netip.AddrPortFrom(sourceIP.Unmap(), uint16(sourcePort)),
		netip.AddrPortFrom(destinationIP.Unmap(), uint16(destinationPort)),
	)
	if uid == -1 {
		return 0, E.New("procfs: not found")
	}
	return uid, nil
}
