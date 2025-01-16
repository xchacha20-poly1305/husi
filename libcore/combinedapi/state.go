package combinedapi

import (
	"net"
	"sync"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing/common/atomic"
	"github.com/sagernet/sing/common/bufio"
	N "github.com/sagernet/sing/common/network"
)

type statsService struct {
	createdAt time.Time
	access    sync.Mutex
	counters  map[string]*atomic.Int64
}

func newStatsService() *statsService {
	return &statsService{
		createdAt: time.Now(),
		counters:  make(map[string]*atomic.Int64),
	}
}

func (s *statsService) queryStats(name string) int64 {
	s.access.Lock()
	counter, loaded := s.counters[name]
	s.access.Unlock()
	if !loaded {
		return 0
	}

	return counter.Swap(0)
}

func (s *statsService) routedConnection(conn net.Conn, matchOutbound adapter.Outbound) net.Conn {
	outbound := matchOutbound.Tag()
	var readCounter []*atomic.Int64
	var writeCounter []*atomic.Int64
	s.access.Lock()
	readCounter = append(readCounter, s.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>uplink"))
	writeCounter = append(writeCounter, s.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>downlink"))
	s.access.Unlock()
	return bufio.NewInt64CounterConn(conn, readCounter, writeCounter)
}

func (s *statsService) routedPacketConnection(conn N.PacketConn, matchOutbound adapter.Outbound) N.PacketConn {
	outbound := matchOutbound.Tag()
	var readCounter []*atomic.Int64
	var writeCounter []*atomic.Int64
	s.access.Lock()
	readCounter = append(readCounter, s.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>uplink"))
	writeCounter = append(writeCounter, s.loadOrCreateCounter("outbound>>>"+outbound+">>>traffic>>>downlink"))
	s.access.Unlock()
	return bufio.NewInt64CounterPacketConn(conn, readCounter, writeCounter)
}

func (s *statsService) loadOrCreateCounter(name string) *atomic.Int64 {
	counter, loaded := s.counters[name]
	if loaded {
		return counter
	}
	counter = &atomic.Int64{}
	s.counters[name] = counter
	return counter
}
