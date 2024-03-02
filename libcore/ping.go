package libcore

import (
	"github.com/xchacha20-poly1305/libping"
)

func IcmpPing(address string, timeout int32) (latency int32, err error) {
	return libping.IcmpPing(address, timeout)
}
