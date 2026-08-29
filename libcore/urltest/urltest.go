// Package urltest is the custom URL test implementation instead of github.com/sagernet/sing-box/common/urltest
package urltest

import (
	"cmp"
	"context"
	"crypto/tls"
	"errors"
	"net"
	"net/http"
	"net/url"
	"time"

	"github.com/sagernet/sing-box/adapter"
	boxurltest "github.com/sagernet/sing-box/common/urltest"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/protocol/group"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/ntp"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const defaultLink = "https://www.gstatic.com/generate_204"

var ErrOutboundNotFound = E.New("outbound is not found")

type Flags uint8

const (
	UnifiedDelay Flags = 1 << iota
	IgnoreHandshakeTime
)

func FlagsFromProto(options *husiv1.URLTestOptions) Flags {
	var flags Flags
	if options.GetUnifiedDelay() {
		flags |= UnifiedDelay
	}
	if options.GetIgnoreHandshakeTime() {
		flags |= IgnoreHandshakeTime
	}
	return flags
}

func WrapError(err error) error {
	if err == nil {
		return nil
	}
	if E.IsCanceled(err) {
		return status.Error(codes.DeadlineExceeded, err.Error())
	}
	if errors.Is(err, ErrOutboundNotFound) {
		return status.Error(codes.NotFound, err.Error())
	}
	return status.Error(codes.Internal, err.Error())
}

// RunTag measures the outbound named tag, or the default outbound when tag is
// empty. instanceCtx is the context of the instance manager belongs to: the
// deadline and the history storage both come from it.
func RunTag(
	instanceCtx context.Context,
	manager adapter.OutboundManager,
	tag, link string,
	timeoutMs int32,
	flags Flags,
) (int32, error) {
	if instanceCtx == nil {
		return -1, E.New("instance context not available")
	}
	if manager == nil {
		return -1, E.New("outbound manager not available")
	}
	var detour adapter.Outbound
	if tag == "" {
		detour = manager.Default()
	} else {
		var loaded bool
		detour, loaded = manager.Outbound(tag)
		if !loaded {
			return -1, E.Cause(ErrOutboundNotFound, tag)
		}
	}
	return Run(instanceCtx, detour, link, timeoutMs, flags)
}

// Run measures detour within timeoutMs and stores the result in the instance's
// URL test history, so the UI sees the same number the group selector does.
func Run(
	instanceCtx context.Context,
	detour adapter.Outbound,
	link string,
	timeoutMs int32,
	flags Flags,
) (int32, error) {
	ctx, cancel := context.WithTimeout(instanceCtx, time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()

	chLatency := make(chan uint16, 1)
	var testErr error
	go func() {
		var latency uint16
		latency, testErr = Measure(ctx, link, detour, flags)
		if testErr != nil {
			close(chLatency)
			return
		}
		chLatency <- latency

		historyStorage := service.PtrFromContext[boxurltest.HistoryStorage](instanceCtx)
		if historyStorage == nil {
			return
		}
		historyStorage.StoreURLTestHistory(group.RealTag(detour), &adapter.URLTestHistory{
			Time:  time.Now(),
			Delay: latency,
		})
	}()
	select {
	case <-ctx.Done():
		return -1, ctx.Err()
	case latency, loaded := <-chLatency:
		if !loaded {
			return -1, testErr
		}
		return int32(latency), nil
	}
}

// Measure times a single HEAD request through detour.
func Measure(ctx context.Context, link string, detour N.Dialer, flags Flags) (t uint16, err error) {
	link = cmp.Or(link, defaultLink)
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
	if flags&IgnoreHandshakeTime != 0 && N.NeedHandshakeForWrite(instance) {
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
	if flags&UnifiedDelay != 0 {
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
