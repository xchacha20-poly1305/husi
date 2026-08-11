package coresvc

import (
	"cmp"
	"context"
	"crypto/tls"
	"net"
	"net/http"
	"net/url"
	"time"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/ntp"
)

const (
	URLTestUnifiedDelay uint8 = 1 << iota
	URLTestIgnoreHandshakeTime
)

func URLTest(ctx context.Context, link string, detour N.Dialer, options uint8) (t uint16, err error) {
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
	if options&URLTestIgnoreHandshakeTime != 0 && N.NeedHandshakeForWrite(instance) {
		start = time.Now()
	}
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
	times := 1
	if options&URLTestUnifiedDelay != 0 {
		times++
	}
	for range times {
		var resp *http.Response
		resp, err = client.Do(req)
		if err != nil {
			return
		}
		t = uint16(time.Since(start) / time.Millisecond)
		_ = resp.Body.Close()
	}
	return
}
