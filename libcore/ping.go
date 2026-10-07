package libcore

import (
	"context"
	"crypto/rand"
	"syscall"
	"time"

	"github.com/sagernet/sing-box"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"

	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/protect"
	"github.com/xchacha20-poly1305/husi/libcore/v2/urltest"
	"github.com/xchacha20-poly1305/libping"
)

func ignoreProtectError() control.Func {
	return func(network, address string, conn syscall.RawConn) error {
		_ = control.Raw(conn, func(fd uintptr) error {
			// Pings must bypass the VPN this process may be running.
			_ = protect.Protect(protectSocketPath, int(fd))
			return nil
		})
		return nil
	}
}

// icmpPing probes address with an ICMP echo. timeout is in milliseconds.
func icmpPing(ctx context.Context, address string, timeout int32) (latency int32, err error) {
	payload := make([]byte, 40)
	_, _ = rand.Read(payload)

	ctx, cancel := context.WithTimeout(ctx, time.Duration(timeout)*time.Millisecond)
	defer cancel()

	t, err := libping.IcmpPing(ctx, M.ParseSocksaddr(address), payload, ignoreProtectError())
	if err != nil {
		return -1, err
	}

	return int32(t.Milliseconds()), nil
}

// tcpPing measures how long a TCP connection to host:port takes to open.
// timeout is in milliseconds.
func tcpPing(ctx context.Context, host string, port uint16, timeout int32) (latency int32, err error) {
	ctx, cancel := context.WithTimeout(ctx, time.Duration(timeout)*time.Millisecond)
	defer cancel()

	l, err := libping.TcpPing(ctx, M.ParseSocksaddrHostPort(host, port), ignoreProtectError())
	if err != nil {
		return -1, err
	}

	return int32(l.Milliseconds()), nil
}

// standaloneURLTest measures an outbound in a throwaway instance,
// leaving the running instance untouched.
func standaloneURLTest(config, tag, link string, timeoutMs int32, options urltest.Flags, platformInterface PlatformInterface) (latency int32, err error) {
	defer catchPanic("standaloneURLTest", func(panicErr error) {
		latency, err = -1, panicErr
	})

	ctx := baseContext(platformInterface)
	boxOptions, err := parseConfig(ctx, config)
	if err != nil {
		return -1, err
	}

	ctx, cancel := context.WithCancel(ctx)
	registerPlatformInterface(ctx, platformInterface, true)

	instance, err := box.New(box.Options{
		Options: boxOptions,
		Context: ctx,
	})
	if err != nil {
		cancel()
		return -1, E.Cause(err, "create instance")
	}
	defer closeBoxTimeout(instance, cancel)

	err = instance.Start()
	if err != nil {
		return -1, E.Cause(err, "start instance")
	}
	return urltest.RunTag(ctx, instance.Outbound(), tag, link, timeoutMs, options)
}

func closeBoxTimeout(instance *box.Box, cancel context.CancelFunc) {
	done := make(chan struct{})
	go func() {
		defer catchPanic("box.Close", func(error) {})
		defer close(done)
		cancel()
		_ = instance.Close()
	}()
	select {
	case <-done:
	case <-time.After(C.FatalStopTimeout):
		// What can I do?
	}
}
