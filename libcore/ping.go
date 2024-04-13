package libcore

import (
	"context"
	"crypto/rand"
	"net"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"

	"github.com/xchacha20-poly1305/libping"
)

func IcmpPing(address string, timeout int32) (latency int32, err error) {
	payload := make([]byte, 20)
	_, _ = rand.Read(payload)

	t, err := libping.IcmpPing(address, (time.Duration)(timeout)*time.Millisecond, payload)
	if err != nil {
		return -1, err
	}

	return int32(t.Milliseconds()), nil
}

func TcpPing(host, port string, timeout int32) (latency int32, err error) {
	address := net.JoinHostPort(host, port)

	start := time.Now()
	conn, err := net.DialTimeout("tcp", address, time.Duration(timeout)*time.Millisecond)
	if err != nil {
		return
	}
	defer conn.Close()

	latency = int32(time.Since(start).Milliseconds())
	return
}

func UrlTest(i *BoxInstance, link string, timeout int32) (latency int32, err error) {
	defer catchPanic("box.UrlTest", func(panicErr error) { err = panicErr })

	var router adapter.Router
	if i == nil {
		// test current
		router = mainInstance.Box.Router()
	} else {
		router = i.Box.Router()
	}

	defOutbound, err := router.DefaultOutbound(N.NetworkTCP)
	if err != nil {
		return 0, E.Cause(err, "find default outbound")
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeout)*time.Millisecond)
	defer cancel()

	chLatency := make(chan uint16, 1)
	go func() {
		var t uint16
		t, err = urltest.URLTest(ctx, link, defOutbound)
		chLatency <- t
	}()
	select {
	case <-ctx.Done():
		return 0, ctx.Err()
	case t := <-chLatency:
		if err != nil {
			return 0, err
		}
		return int32(t), nil
	}
}
