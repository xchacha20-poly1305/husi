package libcore

import (
	"cmp"
	"context"
	"crypto/rand"
	"crypto/tls"
	"io"
	"net"
	"net/http"
	"net/url"
	"syscall"
	"time"

	"libcore/protect"
	"libcore/vario"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/ntp"

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

// urlTest perform URL test for tag using link and timeout as millisecond.
// If tag is empty, it will use the default outbound.
func (b *boxInstance) urlTest(tag, link string, timeout int32) (latency int32, err error) {
	var detour adapter.Outbound
	if tag == "" {
		detour = b.Outbound().Default()
	} else {
		var loaded bool
		detour, loaded = b.Outbound().Outbound(tag)
		if !loaded {
			return -1, E.New(tag, " is not found")
		}
	}

	ctx, cancel := context.WithTimeout(b.ctx, time.Duration(timeout)*time.Millisecond)
	defer cancel()

	// cancel context can't interrupt in time.
	chLatency := make(chan uint16, 1)
	go func() {
		var t uint16
		t, err = urlTest(ctx, link, detour)
		if err != nil {
			close(chLatency)
			return
		}
		chLatency <- t

		historyStorage := b.urlTestHistory
		if historyStorage == nil {
			return
		}
		realTag := group.RealTag(detour)
		historyStorage.StoreURLTestHistory(realTag, &adapter.URLTestHistory{
			Time:  time.Now(),
			Delay: t,
		})
	}()
	select {
	case <-ctx.Done():
		return -1, ctx.Err()
	case t, loaded := <-chLatency:
		if !loaded {
			return -1, err
		}
		return int32(t), nil
	}
}

func (c *Client) NewInstanceURLTest(config, tag, link string, timeout int32) (int32, error) {
	err := vario.WriteUint8(c.conn, commandNewInstanceURLTest)
	if err != nil {
		return -1, E.Cause(err, "write command")
	}
	err = vario.WriteString(c.conn, config)
	if err != nil {
		return -1, E.Cause(err, "write config")
	}
	err = vario.WriteString(c.conn, tag)
	if err != nil {
		return -1, E.Cause(err, "write tag")
	}
	err = vario.WriteString(c.conn, link)
	if err != nil {
		return -1, E.Cause(err, "write link")
	}
	err = vario.WriteInt32(c.conn, timeout)
	if err != nil {
		return -1, E.Cause(err, "write timeout")
	}
	resultCode, err := vario.ReadUint8(c.conn)
	if err != nil {
		return -1, E.Cause(err, "read result code")
	}
	if resultCode != resultNoError {
		errMsg, err := vario.ReadString(c.conn)
		if err != nil {
			return -1, E.Cause(err, "read error message")
		}
		return -1, E.New(errMsg)
	}
	latency, err := vario.ReadInt32(c.conn)
	if err != nil {
		return -1, E.Cause(err, "read latency")
	}
	return latency, nil
}

func (s *Service) handleNewInstanceURLTest(conn io.ReadWriter) error {
	config, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read config")
	}
	tag, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read tag")
	}
	link, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read link")
	}
	timeout, err := vario.ReadInt32(conn)
	if err != nil {
		return E.Cause(err, "read timeout")
	}

	latency, err := s.newInstanceURLTest(config, tag, link, timeout)
	if err != nil {
		_ = vario.WriteUint8(conn, resultCommonError)
		_ = vario.WriteString(conn, err.Error())
		return nil
	}

	err = vario.WriteUint8(conn, resultNoError)
	if err != nil {
		return E.Cause(err, "write result")
	}
	err = vario.WriteInt32(conn, latency)
	if err != nil {
		return E.Cause(err, "write latency")
	}
	return nil
}

func (s *Service) newInstanceURLTest(config, tag, link string, timeout int32) (int32, error) {
	instance, err := newBoxInstance(config, s.platformInterface, true)
	if err != nil {
		return -1, E.Cause(err, "create instance")
	}
	defer instance.Close()
	err = instance.Start()
	if err != nil {
		return -1, E.Cause(err, "start instance")
	}
	return instance.urlTest(tag, link, timeout)
}

func (c *Client) UrlTest(tag, link string, timeout int32) (int32, error) {
	err := vario.WriteUint8(c.conn, commandUrlTest)
	if err != nil {
		return -1, E.Cause(err, "write command")
	}
	err = vario.WriteString(c.conn, tag)
	if err != nil {
		return -1, E.Cause(err, "write tag")
	}
	err = vario.WriteString(c.conn, link)
	if err != nil {
		return -1, E.Cause(err, "write link")
	}
	err = vario.WriteInt32(c.conn, timeout)
	if err != nil {
		return -1, E.Cause(err, "write timeout")
	}
	resultCode, err := vario.ReadUint8(c.conn)
	if err != nil {
		return -1, E.Cause(err, "read result code")
	}
	if resultCode != resultNoError {
		message, err := vario.ReadString(c.conn)
		if err != nil {
			return -1, E.Cause(err, "read error message")
		}
		return -1, E.New(message)
	}
	latency, err := vario.ReadInt32(c.conn)
	if err != nil {
		return -1, E.Cause(err, "read latency")
	}
	return latency, nil
}

func (s *Service) handleUrlTest(conn io.ReadWriter, instance *boxInstance) error {
	tag, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read tag")
	}
	link, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read link")
	}
	timeout, err := vario.ReadInt32(conn)
	if err != nil {
		return E.Cause(err, "read timeout")
	}
	latency, err := instance.urlTest(tag, link, timeout)
	if err != nil {
		_ = vario.WriteUint8(conn, resultCommonError)
		_ = vario.WriteString(conn, err.Error())
		return nil
	}
	err = vario.WriteUint8(conn, resultNoError)
	if err != nil {
		return E.Cause(err, "write result")
	}
	err = vario.WriteInt32(conn, latency)
	if err != nil {
		return E.Cause(err, "write latency")
	}
	return nil
}

// Different from urltest.URLTest: never ignore handshake delay.
func urlTest(ctx context.Context, link string, detour N.Dialer) (t uint16, err error) {
	link = cmp.Or(link, "https://www.gstatic.com/generate_204")
	linkURL, err := url.Parse(link)
	if err != nil {
		return
	}
	hostname := linkURL.Hostname()
	port := linkURL.Port()
	if port == "" {
		switch linkURL.Scheme {
		case "http":
			port = "80"
		case "https":
			port = "443"
		}
	}

	start := time.Now()
	instance, err := detour.DialContext(ctx, N.NetworkTCP, M.ParseSocksaddrHostPortStr(hostname, port))
	if err != nil {
		return
	}
	defer instance.Close()
	req, err := http.NewRequestWithContext(ctx, http.MethodHead, link, nil)
	if err != nil {
		return
	}
	client := http.Client{
		Transport: &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				return instance, nil
			},
			TLSClientConfig: &tls.Config{
				Time:    ntp.TimeFuncFromContext(ctx),
				RootCAs: adapter.RootPoolFromContext(ctx),
			},
		},
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			return http.ErrUseLastResponse
		},
		Timeout: C.TCPTimeout,
	}
	defer client.CloseIdleConnections()
	resp, err := client.Do(req)
	if err != nil {
		return
	}
	defer resp.Body.Close()
	t = uint16(time.Since(start) / time.Millisecond)
	return
}
