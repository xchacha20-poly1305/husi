package v2rayapilite

import (
	"net"
	"sync"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/atomic"
	"github.com/sagernet/sing/common/bufio"
	N "github.com/sagernet/sing/common/network"
)

var (
	_ adapter.V2RayStatsService = (*V2RayServerLite)(nil)
	_ QueryStats                = (*V2RayServerLite)(nil)
)

type V2RayServerLite struct {
	createdAt time.Time
	outbounds map[string]bool
	access    sync.Mutex
	counters  map[string]*atomic.Int64
}

func NewSbV2rayServer(options option.V2RayStatsServiceOptions) *V2RayServerLite {
	if !options.Enabled {
		return nil
	}
	outbounds := make(map[string]bool)
	for _, outbound := range options.Outbounds {
		outbounds[outbound] = true
	}
	return &V2RayServerLite{
		createdAt: time.Now(),
		outbounds: outbounds,
		counters:  make(map[string]*atomic.Int64),
	}
}

func (v *V2RayServerLite) Start() error                            { return nil }
func (v *V2RayServerLite) Close() error                            { return nil }
func (v *V2RayServerLite) StatsService() adapter.V2RayStatsService { return v }

type QueryStats interface {
	QueryStats(name string) int64
}

func (v *V2RayServerLite) QueryStats(name string) int64 {
	v.access.Lock()
	counter, loaded := v.counters[name]
	v.access.Unlock()
	if !loaded {
		return 0
	}

	return counter.Swap(0)
}

func (v *V2RayServerLite) RoutedConnection(inbound string, outbound string, user string, conn net.Conn) net.Conn {
	var readCounter []*atomic.Int64
	var writeCounter []*atomic.Int64
	countOutbound := outbound != "" && v.outbounds[outbound]
	if !countOutbound {
		return conn
	}
	v.access.Lock()
	readCounter = append(readCounter, v.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>uplink"))
	writeCounter = append(writeCounter, v.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>downlink"))
	v.access.Unlock()
	return bufio.NewInt64CounterConn(conn, readCounter, writeCounter)
}

func (v *V2RayServerLite) RoutedPacketConnection(inbound string, outbound string, user string, conn N.PacketConn) N.PacketConn {
	var readCounter []*atomic.Int64
	var writeCounter []*atomic.Int64
	countOutbound := outbound != "" && v.outbounds[outbound]
	if !countOutbound {
		return conn
	}
	v.access.Lock()
	readCounter = append(readCounter, v.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>uplink"))
	writeCounter = append(writeCounter, v.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>downlink"))
	v.access.Unlock()
	return bufio.NewInt64CounterPacketConn(conn, readCounter, writeCounter)
}

//nolint:staticcheck
func (v *V2RayServerLite) loadOrCreateCounter(name string) *atomic.Int64 {
	counter, loaded := v.counters[name]
	if loaded {
		return counter
	}
	counter = &atomic.Int64{}
	v.counters[name] = counter
	return counter
}
