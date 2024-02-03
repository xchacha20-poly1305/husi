package api

import (
	"net"
	"sync"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/atomic"
	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"
)

var (
	_ adapter.V2RayStatsService = (*SbStatsService)(nil)
)

// SbStatsService
// note: removed inbound and user
type SbStatsService struct {
	createdAt time.Time
	outbounds map[string]bool
	access    sync.Mutex
	counters  map[string]*atomic.Int64
}

func NewSbStatsService(options option.V2RayStatsServiceOptions) *SbStatsService {
	if !options.Enabled {
		return nil
	}
	outbounds := make(map[string]bool)
	for _, outbound := range options.Outbounds {
		outbounds[outbound] = true
	}
	return &SbStatsService{
		createdAt: time.Now(),
		outbounds: outbounds,
		counters:  make(map[string]*atomic.Int64),
	}
}

func (s *SbStatsService) RoutedConnection(inbound string, outbound string, user string, conn net.Conn) net.Conn {
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

func (s *SbStatsService) RoutedPacketConnection(inbound string, outbound string, user string, conn N.PacketConn) N.PacketConn {
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

func (s *SbStatsService) GetStats(name string, reset bool) (int64, error) {
	s.access.Lock()
	counter, loaded := s.counters[name]
	s.access.Unlock()
	if !loaded {
		return 0, E.New(name, " not found.")
	}
	var value int64
	if reset {
		value = counter.Swap(0)
	} else {
		value = counter.Load()
	}
	return value, nil
}

// QueryStats

// GetSysStats

//nolint:staticcheck
func (s *SbStatsService) loadOrCreateCounter(name string) *atomic.Int64 {
	counter, loaded := s.counters[name]
	if loaded {
		return counter
	}
	counter = &atomic.Int64{}
	s.counters[name] = counter
	return counter
}
