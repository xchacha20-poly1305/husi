package libcore

import (
	"context"
	"crypto/rand"
	"net"
	"strconv"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"

	"libcore/protect"

	"github.com/xchacha20-poly1305/libping"
	"golang.org/x/sys/unix"
)

func IcmpPing(address string, timeout int32, shouldProtect bool) (latency int32, err error) {
	if shouldProtect {
		libping.Protect = func(fd int) {
			_ = protect.ClientProtect(fd, ProtectPath)
		}
	} else {
		libping.Protect = nil
	}

	payload := make([]byte, 20)
	_, _ = rand.Read(payload)

	t, err := libping.IcmpPing(address, (time.Duration)(timeout)*time.Millisecond, payload)
	if err != nil {
		return -1, err
	}

	return int32(t.Milliseconds()), nil
}

func TcpPing(host, port string, timeout int32, shouldProtect bool) (latency int32, err error) {
	log.Trace(host)
	ip := net.ParseIP(host)
	if ip == nil {
		return -1, E.New("failed to parse ip: ", host)
	}
	isIPv6 := ip.To4() == nil

	var socketFd int
	if isIPv6 {
		socketFd, err = unix.Socket(unix.AF_INET6, unix.SOCK_STREAM, 0)
	} else {
		socketFd, err = unix.Socket(unix.AF_INET, unix.SOCK_STREAM, 0)
	}
	if err != nil {
		return -1, err
	}
	defer unix.Close(socketFd)

	if shouldProtect {
		_ = protect.ClientProtect(socketFd, ProtectPath)
	}

	var sockAddr unix.Sockaddr
	portInt, _ := strconv.Atoi(port)
	if isIPv6 {
		sockAddr = &unix.SockaddrInet6{Port: portInt, Addr: [16]byte(ip.To16())}
	} else {
		sockAddr = &unix.SockaddrInet4{Port: portInt, Addr: [4]byte(ip.To4())}
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeout)*time.Millisecond)
	defer cancel()
	errCh := make(chan error, 1)
	start := time.Now()
	go func() {
		errCh <- unix.Connect(socketFd, sockAddr)
	}()
	select {
	case <-ctx.Done():
		return -1, E.New("TCP ping timeout")
	case err := <-errCh:
		if err != nil {
			return -1, err
		}
		return int32(time.Since(start).Milliseconds()), nil
	}
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
