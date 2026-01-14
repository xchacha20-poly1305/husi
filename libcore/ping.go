package libcore

import (
	"context"
	"crypto/rand"
	"io"
	"sync"
	"syscall"
	"time"

	"libcore/protect"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/binary"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/varbin"

	"github.com/xchacha20-poly1305/libping"
	"golang.org/x/sync/errgroup"
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
	defer catchPanic("box.urlTest", func(panicErr error) { err = panicErr })

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

	// cancel context can't interrupt in time.
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
	case t, loaded := <-chLatency:
		if !loaded {
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

func (c *Client) GroupTest(tag, link string, timeout int32) (ResultPairIterator, error) {
	err := varbin.Write(c.conn, binary.BigEndian, commandGroupURLTest)
	if err != nil {
		return nil, E.Cause(err, "write command")
	}
	err = varbin.Write(c.conn, binary.BigEndian, tag)
	if err != nil {
		return nil, E.Cause(err, "write tag")
	}
	err = varbin.Write(c.conn, binary.BigEndian, link)
	if err != nil {
		return nil, E.Cause(err, "write link")
	}
	err = varbin.Write(c.conn, binary.BigEndian, timeout)
	if err != nil {
		return nil, E.Cause(err, "write timeout")
	}
	resultCode, err := varbin.ReadValue[uint8](c.conn, binary.BigEndian)
	if err != nil {
		return nil, E.Cause(err, "read result code")
	}
	if resultCode != resultNoError {
		message, err := varbin.ReadValue[string](c.conn, binary.BigEndian)
		if err != nil {
			return nil, E.Cause(err, "read error message")
		}
		return nil, E.New(message)
	}
	results, err := varbin.ReadValue[[]*ResultPair](c.conn, binary.BigEndian)
	if err != nil {
		return nil, E.Cause(err, "read results")
	}
	return newIterator(results), nil
}

func (s *Service) handleGroupTest(conn io.ReadWriter, instance *boxInstance) error {
	tag, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read tag")
	}
	link, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read link")
	}
	timeout, err := varbin.ReadValue[int32](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read timeout")
	}

	outboundManager := instance.Outbound()
	outbound, loaded := outboundManager.Outbound(tag)
	if !loaded {
		err = E.New("group [", tag, "] is not found")
		_ = varbin.Write(conn, binary.BigEndian, resultCommonError)
		_ = varbin.Write(conn, binary.BigEndian, err.Error())
		return err
	}
	outboundGroup, isOutboundGroup := outbound.(adapter.OutboundGroup)
	if !isOutboundGroup {
		err = E.New("[", tag, "] is not a group")
		_ = varbin.Write(conn, binary.BigEndian, resultCommonError)
		_ = varbin.Write(conn, binary.BigEndian, err.Error())
		return err
	}

	ctx, cancel := context.WithTimeout(instance.ctx, time.Duration(timeout)*time.Millisecond)
	defer cancel()

	var results []*ResultPair
	if urlTestGroup, isURLTestGroup := outboundGroup.(adapter.URLTestGroup); isURLTestGroup {
		uintResult, err := urlTestGroup.URLTest(ctx)
		if err != nil {
			_ = varbin.Write(conn, binary.BigEndian, resultCommonError)
			_ = varbin.Write(conn, binary.BigEndian, err.Error())
			return err
		}
		results = make([]*ResultPair, 0, len(uintResult))
		for tag, delay := range uintResult {
			results = append(results, &ResultPair{
				Tag:   tag,
				Delay: int16(delay),
			})
		}
	} else {
		outbounds := common.FilterNotNil(common.Map(outboundGroup.All(), func(it string) adapter.Outbound {
			itOutbound, _ := outboundManager.Outbound(it)
			return itOutbound
		}))
		errGroup, _ := errgroup.WithContext(ctx)
		errGroup.SetLimit(10)
		checked := make(map[string]bool)
		results = make([]*ResultPair, 0, len(outbounds))
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
			errGroup.Go(func() error {
				t, err := urltest.URLTest(ctx, link, p)
				if err != nil {
					log.DebugContext(ctx, "outbound ", tag, " unavailable: ", err)
				} else {
					log.DebugContext(ctx, "outbound ", tag, " available: ", t, "ms")
					resultAccess.Lock()
					results = append(results, &ResultPair{
						Tag:   tag,
						Delay: int16(t),
					})
					resultAccess.Unlock()
				}
				return nil
			})
		}
		_ = errGroup.Wait()
	}

	err = varbin.Write(conn, binary.BigEndian, resultNoError)
	if err != nil {
		return E.Cause(err, "write result code")
	}
	err = varbin.Write(conn, binary.BigEndian, results)
	if err != nil {
		return E.Cause(err, "write results")
	}
	return nil
}

func (c *Client) NewInstanceURLTest(config, tag, link string, timeout int32) (int32, error) {
	err := varbin.Write(c.conn, binary.BigEndian, commandNewInstanceURLTest)
	if err != nil {
		return -1, E.Cause(err, "write command")
	}
	err = varbin.Write(c.conn, binary.BigEndian, config)
	if err != nil {
		return -1, E.Cause(err, "write config")
	}
	err = varbin.Write(c.conn, binary.BigEndian, tag)
	if err != nil {
		return -1, E.Cause(err, "write tag")
	}
	err = varbin.Write(c.conn, binary.BigEndian, link)
	if err != nil {
		return -1, E.Cause(err, "write link")
	}
	err = varbin.Write(c.conn, binary.BigEndian, timeout)
	if err != nil {
		return -1, E.Cause(err, "write timeout")
	}
	resultCode, err := varbin.ReadValue[uint8](c.conn, binary.BigEndian)
	if err != nil {
		return -1, E.Cause(err, "read result code")
	}
	if resultCode != resultNoError {
		errMsg, err := varbin.ReadValue[string](c.conn, binary.BigEndian)
		if err != nil {
			return -1, E.Cause(err, "read error message")
		}
		return -1, E.New(errMsg)
	}
	latency, err := varbin.ReadValue[int32](c.conn, binary.BigEndian)
	if err != nil {
		return -1, E.Cause(err, "read latency")
	}
	return latency, nil
}

func (s *Service) handleNewInstanceURLTest(conn io.ReadWriter) error {
	config, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read config")
	}
	tag, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read tag")
	}
	link, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read link")
	}
	timeout, err := varbin.ReadValue[int32](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read timeout")
	}

	latency, err := s.doURLTest(config, tag, link, timeout)
	if err != nil {
		_ = varbin.Write(conn, binary.BigEndian, resultCommonError)
		_ = varbin.Write(conn, binary.BigEndian, err.Error())
		return nil
	}

	err = varbin.Write(conn, binary.BigEndian, resultNoError)
	if err != nil {
		return E.Cause(err, "write result")
	}
	err = varbin.Write(conn, binary.BigEndian, latency)
	if err != nil {
		return E.Cause(err, "write latency")
	}
	return nil
}

func (s *Service) doURLTest(config, tag, link string, timeout int32) (int32, error) {
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
	err := varbin.Write(c.conn, binary.BigEndian, commandUrlTest)
	if err != nil {
		return -1, E.Cause(err, "write command")
	}
	err = varbin.Write(c.conn, binary.BigEndian, tag)
	if err != nil {
		return -1, E.Cause(err, "write tag")
	}
	err = varbin.Write(c.conn, binary.BigEndian, link)
	if err != nil {
		return -1, E.Cause(err, "write link")
	}
	err = varbin.Write(c.conn, binary.BigEndian, timeout)
	if err != nil {
		return -1, E.Cause(err, "write timeout")
	}
	resultCode, err := varbin.ReadValue[uint8](c.conn, binary.BigEndian)
	if err != nil {
		return -1, E.Cause(err, "read result code")
	}
	if resultCode != resultNoError {
		message, err := varbin.ReadValue[string](c.conn, binary.BigEndian)
		if err != nil {
			return -1, E.Cause(err, "read error message")
		}
		return -1, E.New(message)
	}
	latency, err := varbin.ReadValue[int32](c.conn, binary.BigEndian)
	if err != nil {
		return -1, E.Cause(err, "read latency")
	}
	return latency, nil
}

func (s *Service) handleUrlTest(conn io.ReadWriter, instance *boxInstance) error {
	tag, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read tag")
	}
	link, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read link")
	}
	timeout, err := varbin.ReadValue[int32](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read timeout")
	}
	latency, err := instance.urlTest(tag, link, timeout)
	if err != nil {
		_ = varbin.Write(conn, binary.BigEndian, resultCommonError)
		_ = varbin.Write(conn, binary.BigEndian, err.Error())
		return nil
	}
	err = varbin.Write(conn, binary.BigEndian, resultNoError)
	if err != nil {
		return E.Cause(err, "write result")
	}
	err = varbin.Write(conn, binary.BigEndian, latency)
	if err != nil {
		return E.Cause(err, "write latency")
	}
	return nil
}
