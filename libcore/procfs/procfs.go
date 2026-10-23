package procfs

import (
	"bufio"
	"encoding/binary"
	"encoding/hex"
	"net"
	"net/netip"
	"os"
	"slices"
	"strconv"
	"strings"

	N "github.com/sagernet/sing/common/network"
)

var (
	netIndexOfLocal  = -1
	netIndexOfRemote = -1
	netIndexOfUid    = -1
	netIndexOfInode  = -1
)

func ResolveSocketByProcSearch(network string, source, destination netip.AddrPort) int32 {
	if netIndexOfLocal < 0 || netIndexOfRemote < 0 || netIndexOfUid < 0 || netIndexOfInode < 0 {
		return -1
	}
	path := "/proc/net/" + network
	if source.Addr().Is4() {
		uid := searchSocketTable(path, network, source, destination)
		if uid != -1 {
			return uid
		}
		// java.net sockets on Android are always dual-stack AF_INET6, so their IPv4 connections are only listed in the IPv6 table.
		source = netip.AddrPortFrom(netip.AddrFrom16(source.Addr().As16()), source.Port())
		if destination.Addr().Is4() {
			destination = netip.AddrPortFrom(netip.AddrFrom16(destination.Addr().As16()), destination.Port())
		}
	}
	return searchSocketTable(path+"6", network, source, destination)
}

func searchSocketTable(path string, network string, source, destination netip.AddrPort) int32 {
	file, err := os.Open(path)
	if err != nil {
		return -1
	}
	defer file.Close()

	local := formatSocketAddress(source)
	var remote string
	if destination.IsValid() {
		remote = formatSocketAddress(destination)
	}
	// An unconnected UDP socket may be bound to the unspecified address of its family.
	var unspecifiedAddresses []string
	if network == N.NetworkUDP {
		if source.Addr().Is4() {
			unspecifiedAddresses = append(unspecifiedAddresses, formatSocketAddress(netip.AddrPortFrom(netip.IPv4Unspecified(), source.Port())))
		} else {
			unspecifiedAddresses = append(unspecifiedAddresses, formatSocketAddress(netip.AddrPortFrom(netip.IPv6Unspecified(), source.Port())))
			if source.Addr().Is4In6() {
				unspecifiedAddresses = append(unspecifiedAddresses, formatSocketAddress(netip.AddrPortFrom(netip.AddrFrom16(netip.IPv4Unspecified().As16()), source.Port())))
			}
		}
	}

	const (
		rankUnspecifiedLocal = iota + 1
		rankLocal
		rankLocalAndRemote
	)
	minFields := max(netIndexOfLocal, netIndexOfRemote, netIndexOfUid, netIndexOfInode) + 1
	var (
		bestRank int
		bestUID  int32 = -1
	)
	reader := bufio.NewReader(file)
	for {
		row, _, err := reader.ReadLine()
		if err != nil {
			return bestUID
		}

		fields := strings.Fields(string(row))
		// Sockets in TIME_WAIT have no inode and report uid 0.
		if len(fields) < minFields || fields[netIndexOfInode] == "0" && fields[netIndexOfUid] == "0" {
			continue
		}

		localAddress := fields[netIndexOfLocal]
		var rank int
		switch {
		case strings.EqualFold(localAddress, local) && (remote == "" || strings.EqualFold(fields[netIndexOfRemote], remote)):
			rank = rankLocalAndRemote
		case network != N.NetworkUDP:
			continue
		case strings.EqualFold(localAddress, local):
			rank = rankLocal
		case slices.ContainsFunc(unspecifiedAddresses, func(address string) bool { return strings.EqualFold(localAddress, address) }):
			rank = rankUnspecifiedLocal
		default:
			continue
		}
		if rank <= bestRank {
			continue
		}

		uid, err := strconv.Atoi(fields[netIndexOfUid])
		if err != nil {
			continue
		}
		if rank == rankLocalAndRemote {
			return int32(uid)
		}
		bestRank = rank
		bestUID = int32(uid)
	}
}

func formatSocketAddress(address netip.AddrPort) string {
	var port [2]byte
	binary.BigEndian.PutUint16(port[:], address.Port())
	return hex.EncodeToString(nativeEndianIP(address.Addr().AsSlice())) + ":" + hex.EncodeToString(port[:])
}

func nativeEndianIP(ip net.IP) []byte {
	result := make([]byte, len(ip))

	for i := 0; i < len(ip); i += 4 {
		value := binary.BigEndian.Uint32(ip[i:])
		binary.NativeEndian.PutUint32(result[i:], value)
	}

	return result
}

func init() {
	file, err := os.Open("/proc/net/tcp")
	if err != nil {
		return
	}

	defer file.Close()

	reader := bufio.NewReader(file)

	header, _, err := reader.ReadLine()
	if err != nil {
		return
	}

	columns := strings.Fields(string(header))

	var txQueue, rxQueue, tr, tmWhen bool

	for i, column := range columns {
		offset := 0

		if txQueue && rxQueue {
			offset--
		}

		if tr && tmWhen {
			offset--
		}

		switch column {
		case "tx_queue":
			txQueue = true
		case "rx_queue":
			rxQueue = true
		case "tr":
			tr = true
		case "tm->when":
			tmWhen = true
		case "local_address":
			netIndexOfLocal = i + offset
		case "rem_address":
			netIndexOfRemote = i + offset
		case "uid":
			netIndexOfUid = i + offset
		case "inode":
			netIndexOfInode = i + offset
		}
	}
}
