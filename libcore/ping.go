package libcore

import (
	"context"
	"crypto/rand"
	"syscall"
	"time"

	"libcore/protect"

	"github.com/sagernet/sing-box/common/urltest"
	"github.com/sagernet/sing/common/control"
	M "github.com/sagernet/sing/common/metadata"

	"github.com/xchacha20-poly1305/libping"
)

func ignoreProtectError() control.Func {
	return func(network, address string, conn syscall.RawConn) error {
		_ = control.Raw(conn, func(fd uintptr) error {
			_ = protect.Protect(ProtectPath, int(fd))
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

// UrlTest try to use default outbound to connect to link. `timeout` is Millisecond.
func (b *BoxInstance) UrlTest(link string, timeout int32) (latency int32, err error) {
	defer catchPanic("box.UrlTest", func(panicErr error) { err = panicErr })

	ctx, cancel := context.WithTimeout(b.ctx, time.Duration(timeout)*time.Millisecond)
	defer cancel()

	// cancel context can't interrupt it in time.
	chLatency := make(chan uint16, 1)
	go func() {
		var t uint16
		t, err = urltest.URLTest(ctx, link, b.Outbound().Default())
		if err != nil {
			close(chLatency)
			return
		}
		chLatency <- t
	}()
	select {
	case <-ctx.Done():
		return -1, ctx.Err()
	case t, ok := <-chLatency:
		if !ok {
			return -1, err
		}
		return int32(t), nil
	}
}
