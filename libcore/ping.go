package libcore

import (
	"context"
	"crypto/rand"
	"sync"
	"syscall"
	"time"

	"libcore/protect"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/batch"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

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

// UrlTest perform URL test for tag using link and timeout as millisecond.
// If tag is empty, it will use the default outbound.
func (b *BoxInstance) UrlTest(tag, link string, timeout int32) (latency int32, err error) {
	defer catchPanic("box.UrlTest", func(panicErr error) { err = panicErr })

	var detour N.Dialer
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

	// cancel context can't interrupt it in time.
	chLatency := make(chan uint16, 1)
	go func() {
		var t uint16
		t, err = urltest.URLTest(ctx, link, detour)
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

type ResultPair struct {
	Tag   string
	Delay int16
}

type ResultPairIterator interface {
	Next() *ResultPair
	HasNext() bool
	Length() int32
}

func (b *BoxInstance) GroupTest(tag, link string, timeout int32) (ResultPairIterator, error) {
	outboundManager := b.Outbound()
	outbound, loaded := outboundManager.Outbound(tag)
	if !loaded {
		return nil, E.New("group [", tag, "] is not found")
	}
	outboundGroup, isOutboundGroup := outbound.(adapter.OutboundGroup)
	if !isOutboundGroup {
		return nil, E.New("[", tag, "] is not a group")
	}

	ctx, cancel := context.WithTimeout(b.ctx, time.Duration(timeout)*time.Millisecond)
	defer cancel()

	var result []*ResultPair
	if urlTestGroup, isURLTestGroup := outboundGroup.(adapter.URLTestGroup); isURLTestGroup {
		uintResult, err := urlTestGroup.URLTest(ctx)
		if err != nil {
			return nil, err
		}
		result = make([]*ResultPair, 0, len(uintResult))
		for tag, delay := range uintResult {
			result = append(result, &ResultPair{
				Tag:   tag,
				Delay: int16(delay),
			})
		}
	} else {
		outbounds := common.FilterNotNil(common.Map(outboundGroup.All(), func(it string) adapter.Outbound {
			itOutbound, _ := outboundManager.Outbound(it)
			return itOutbound
		}))
		b, _ := batch.New(ctx, batch.WithConcurrencyNum[any](10))
		checked := make(map[string]bool)
		result = make([]*ResultPair, 0, len(outbounds))
		var resultAccess sync.Mutex
		for _, detour := range outbounds {
			tag := detour.Tag()
			realTag := group.RealTag(detour)
			if checked[realTag] {
				continue
			}
			checked[realTag] = true
			p, loaded := outboundManager.Outbound(realTag)
			if !loaded {
				continue
			}
			b.Go(realTag, func() (any, error) {
				t, err := urltest.URLTest(ctx, link, p)
				if err != nil {
					log.DebugContext(ctx, "outbound ", tag, " unavailable: ", err)
				} else {
					log.DebugContext(ctx, "outbound ", tag, " available: ", t, "ms")
					resultAccess.Lock()
					result = append(result, &ResultPair{
						Tag:   tag,
						Delay: int16(t),
					})
					resultAccess.Unlock()
				}
				return nil, nil
			})
		}
		b.Wait()
	}

	return newIterator(result), nil
}
