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
	_ adapter.V2RayStatsService = (*StatsService)(nil)
	_ StatsGetter               = (*StatsService)(nil)
)

type StatsService struct {
	createdAt time.Time
	outbounds map[string]bool
	access    sync.Mutex
	counters  map[string]*atomic.Int64
}

func NewStatsService(options option.V2RayStatsServiceOptions) *StatsService {
	if !options.Enabled {
		return nil
	}
	outbounds := make(map[string]bool)
	for _, outbound := range options.Outbounds {
		outbounds[outbound] = true
	}
	return &StatsService{
		createdAt: time.Now(),
		outbounds: outbounds,
		counters:  make(map[string]*atomic.Int64),
	}
}

func (s *StatsService) QueryStats(name string) int64 {
	s.access.Lock()
	counter, loaded := s.counters[name]
	s.access.Unlock()
	if !loaded {
		return 0
	}

	return counter.Swap(0)
}

func (s *StatsService) RoutedConnection(inbound string, outbound string, user string, conn net.Conn) net.Conn {
	var readCounter []*atomic.Int64
	var writeCounter []*atomic.Int64
	countOutbound := outbound != "" && s.outbounds[outbound]
	if !countOutbound {
		return conn
	}
	s.access.Lock()
	readCounter = append(readCounter, s.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>uplink"))
	writeCounter = append(writeCounter, s.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>downlink"))
	s.access.Unlock()
	return bufio.NewInt64CounterConn(conn, readCounter, writeCounter)
}

func (s *StatsService) RoutedPacketConnection(inbound string, outbound string, user string, conn N.PacketConn) N.PacketConn {
	var readCounter []*atomic.Int64
	var writeCounter []*atomic.Int64
	countOutbound := outbound != "" && s.outbounds[outbound]
	if !countOutbound {
		return conn
	}
	s.access.Lock()
	readCounter = append(readCounter, s.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>uplink"))
	writeCounter = append(writeCounter, s.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>downlink"))
	s.access.Unlock()
	return bufio.NewInt64CounterPacketConn(conn, readCounter, writeCounter)
}

//nolint:staticcheck
func (s *StatsService) loadOrCreateCounter(name string) *atomic.Int64 {
	counter, loaded := s.counters[name]
	if loaded {
		return counter
	}
	counter = &atomic.Int64{}
	s.counters[name] = counter
	return counter
}
