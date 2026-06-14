// Package trafficstats provides per-outbound traffic static.
package trafficstats

import (
	"context"
	"net"
	"sync/atomic"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/compatible"
	"github.com/sagernet/sing/common/bufio"
	N "github.com/sagernet/sing/common/network"
)

var _ adapter.ConnectionTracker = (*Tracker)(nil)

type Tracker struct {
	outbound adapter.OutboundManager
	counters compatible.Map[string, *outboundCounter]
}

type outboundCounter struct {
	upload   atomic.Int64
	download atomic.Int64
}

func NewTracker(outbound adapter.OutboundManager) *Tracker {
	return &Tracker{outbound: outbound}
}

func (t *Tracker) RoutedConnection(ctx context.Context, conn net.Conn, metadata adapter.InboundContext, matchedRule adapter.Rule, matchOutbound adapter.Outbound) net.Conn {
	tracker := t.counterFor(matchOutbound)
	if tracker == nil {
		return conn
	}
	return bufio.NewCounterConn(conn, []N.CountFunc{func(n int64) {
		tracker.upload.Add(n)
	}}, []N.CountFunc{func(n int64) {
		tracker.download.Add(n)
	}})
}

func (t *Tracker) RoutedPacketConnection(ctx context.Context, conn N.PacketConn, metadata adapter.InboundContext, matchedRule adapter.Rule, matchOutbound adapter.Outbound) N.PacketConn {
	tracker := t.counterFor(matchOutbound)
	if tracker == nil {
		return conn
	}
	return bufio.NewCounterPacketConn(conn, []N.CountFunc{func(n int64) {
		tracker.upload.Add(n)
	}}, []N.CountFunc{func(n int64) {
		tracker.download.Add(n)
	}})
}

func (t *Tracker) QueryStats(tag string, isUpload bool) int64 {
	counter, loaded := t.counters.Load(tag)
	if !loaded {
		return 0
	}
	if isUpload {
		return counter.upload.Swap(0)
	}
	return counter.download.Swap(0)
}

func (t *Tracker) counterFor(outbound adapter.Outbound) *outboundCounter {
	var tag string
	if outbound == nil {
		if t.outbound != nil {
			outbound = t.outbound.Default()
		}
	}
	if outbound != nil {
		tag = outbound.Tag()
	}
	if tag == "" {
		return nil
	}
	counter, _ := t.counters.LoadOrStore(tag, new(outboundCounter))
	return counter
}
