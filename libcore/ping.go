package libcore

import (
	"context"
	"crypto/rand"
	"syscall"
	"time"

	"libcore/coresvc"
	"libcore/plugin/protect"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"

	"github.com/xchacha20-poly1305/libping"
)

func ignoreProtectError() control.Func {
	return func(network, address string, conn syscall.RawConn) error {
		_ = control.Raw(conn, func(fd uintptr) error {
			// Pings run in the UI process, which has no VPN service of its own.
			_ = protect.Protect(protectSocketPath, int(fd))
			return nil
		})
		return nil
	}
}

// IcmpPing use ICMP to probe the address. `timeout` is Millisecond.
func IcmpPing(address string, timeout int32) (latency int32, err error) {
	payload := make([]byte, 40)
	_, _ = rand.Read(payload)

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeout)*time.Millisecond)
	defer cancel()

	t, err := libping.IcmpPing(ctx, M.ParseSocksaddr(address), payload, ignoreProtectError())
	if err != nil {
		return -1, err
	}

	return int32(t.Milliseconds()), nil
}

// TcpPing try create TCP connection to target. `timeout` is Millisecond.
func TcpPing(host, port string, timeout int32) (latency int32, err error) {
	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeout)*time.Millisecond)
	defer cancel()

	l, err := libping.TcpPing(ctx, M.ParseSocksaddrHostPortStr(host, port), ignoreProtectError())
	if err != nil {
		return -1, err
	}

	return int32(l.Milliseconds()), nil
}

// urlTest perform URL test for tag using link and timeout as millisecond.
// If tag is empty, it will use the default outbound.
// Used by the forTest StandaloneURLTest path.
func (b *boxInstance) urlTest(tag, link string, timeout int32, options uint8) (latency int32, err error) {
	var detour adapter.Outbound
	if tag == "" {
		detour = b.Outbound().Default()
	} else {
		var loaded bool
		detour, loaded = b.Outbound().Outbound(tag)
		if !loaded {
			return -1, E.Cause(coresvc.ErrOutboundNotFound, tag)
		}
	}
	// History storage is on the instance context for the forTest path.
	return coresvc.RunOutboundURLTest(b.ctx, detour, link, timeout, options)
}
