package procfs

import (
	"bufio"
	"encoding/binary"
	"encoding/hex"
	"net"
	"net/netip"
	"os"
	"strconv"
	"strings"

	F "github.com/sagernet/sing/common/format"
	N "github.com/sagernet/sing/common/network"
)

var (
	netIndexOfLocal = -1
	netIndexOfUid   = -1
)

func ResolveSocketByProcSearch(network string, source, _ netip.AddrPort) int32 {
	if netIndexOfLocal < 0 || netIndexOfUid < 0 {
		return -1
	}

	path := "/proc/net/"
	if network == N.NetworkTCP {
		path += "tcp"
	} else {
		path += "udp"
	}
	if source.Addr().Is6() {
		path += "6"
	}

	sIP := source.Addr().AsSlice()
	if len(sIP) == 0 {
		return -1
	}

	var port [2]byte
	binary.BigEndian.PutUint16(port[:], source.Port())

	local := F.ToString(hex.EncodeToString(nativeEndianIP(sIP)), ":", hex.EncodeToString(port[:]))

	file, err := os.Open(path)
	if err != nil {
		return -1
	}
	defer file.Close()

	reader := bufio.NewReader(file)
	for {
		row, _, err := reader.ReadLine()
		if err != nil {
			return -1
		}

		fields := strings.Fields(string(row))
		if len(fields) <= netIndexOfLocal || len(fields) <= netIndexOfUid {
			continue
		}

		if strings.EqualFold(local, fields[netIndexOfLocal]) {
			uid, err := strconv.Atoi(fields[netIndexOfUid])
			if err != nil {
				return -1
			}

			return int32(uid)
		}
	}
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
		case "uid":
			netIndexOfUid = i + offset
		}
	}
}
