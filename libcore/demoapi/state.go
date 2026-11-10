package demoapi

import (
	"context"
	"fmt"
	"slices"
	"sync"
	"time"

	"github.com/sagernet/sing-box/daemon"
)

const (
	simulationInterval   = time.Second
	maxLogs              = 200
	maxClosedConnections = 100
	targetConnections    = 8
	connectionPeriod     = 5
	connectionLifetime   = 30 * time.Second
)

type service struct {
	daemon.UnimplementedStartedServiceServer
	ctx            context.Context
	access         sync.Mutex
	changed        chan struct{}
	startedAt      time.Time
	tick           uint64
	nextConnection uint64
	uploadTotal    int64
	downloadTotal  int64
	groups         []*daemon.Group
	outbounds      []*daemon.GroupItem
	connections    []*daemon.Connection
	connectionLogs map[string]context.Context
	mode           string
	logs           []*daemon.Log_Message
	logSequence    uint64
	logGeneration  uint64
}

func newService(ctx context.Context, now time.Time) *service {
	nodes := []*daemon.GroupItem{
		{Tag: "Tokyo", Type: "shadowsocks", UrlTestDelay: 42},
		{Tag: "Singapore", Type: "trojan", UrlTestDelay: 18},
		{Tag: "Hong Kong", Type: "vless", UrlTestDelay: 35},
		{Tag: "Frankfurt", Type: "hysteria2", UrlTestDelay: 168},
		{Tag: "Los Angeles", Type: "vmess", UrlTestDelay: 142},
		{Tag: "Sydney", Type: "tuic", UrlTestDelay: 95},
	}
	for _, node := range nodes {
		node.UrlTestTime = now.Unix()
	}
	s := &service{
		ctx: ctx, changed: make(chan struct{}), startedAt: now,
		mode: "rule", connectionLogs: make(map[string]context.Context),
		groups: []*daemon.Group{
			{Tag: "Proxy", Type: "selector", Selectable: true, Selected: "Singapore", IsExpand: true, Items: nodes},
			{Tag: "Auto", Type: "urltest", Selected: "Singapore", IsExpand: true, Items: nodes},
		},
		outbounds: append(slices.Clone(nodes), &daemon.GroupItem{Tag: "DIRECT", Type: "direct"}),
	}
	s.appendStartupLogs(now)
	for range targetConnections {
		s.addConnection(now)
	}
	// Keep a closed example available before the first simulation tick.
	s.addConnection(now)
	s.closeConnection(s.connections[len(s.connections)-1], now)
	return s
}

func (s *service) run() {
	ticker := time.NewTicker(simulationInterval)
	defer ticker.Stop()
	for {
		select {
		case <-s.ctx.Done():
			return
		case now := <-ticker.C:
			s.advance(now)
		}
	}
}

func (s *service) advance(now time.Time) {
	s.access.Lock()
	defer s.access.Unlock()
	s.tick++
	for i, connection := range s.connections {
		if connection.ClosedAt != 0 {
			continue
		}
		if now.Sub(time.UnixMilli(connection.CreatedAt)) >= connectionLifetime {
			s.closeConnection(connection, now)
			continue
		}
		phase := (s.tick + uint64(i)*3) % 20
		connection.Uplink = int64(phase+1) * 1024
		connection.Downlink = int64(20-phase) * 32 * 1024
		connection.UplinkTotal += connection.Uplink
		connection.DownlinkTotal += connection.Downlink
		s.uploadTotal += connection.Uplink
		s.downloadTotal += connection.Downlink
	}
	if s.tick%connectionPeriod == 0 {
		for s.activeConnections() < targetConnections {
			s.addConnection(now)
		}
	}
	if s.tick%3 == 0 {
		s.appendDNSLogs(now)
	}
	s.trimConnections()
	s.notify()
}

// State mutations and notification replacement share the lock, so a subscriber
// cannot miss a change between taking a snapshot and waiting for the next one.
func (s *service) notify() {
	close(s.changed)
	s.changed = make(chan struct{})
}

func (s *service) activeConnections() int {
	count := 0
	for _, connection := range s.connections {
		if connection.ClosedAt == 0 {
			count++
		}
	}
	return count
}

func (s *service) trimConnections() {
	excess := len(s.connections) - s.activeConnections() - maxClosedConnections
	s.connections = slices.DeleteFunc(s.connections, func(connection *daemon.Connection) bool {
		if excess > 0 && connection.ClosedAt != 0 {
			excess--
			return true
		}
		return false
	})
}

func (s *service) addConnection(now time.Time) {
	s.nextConnection++
	id := s.nextConnection
	profiles := []struct{ domain, protocol, network, process string }{
		{"docs.example", "tls", "tcp", "browser"},
		{"video.example", "quic", "udp", "video-player"},
		{"code.example", "tls", "tcp", "editor"},
		{"chat.example", "tls", "tcp", "messenger"},
	}
	profile := profiles[(id-1)%uint64(len(profiles))]
	outbound := s.groups[0].Selected
	rule := "domain_suffix=.example => Proxy"
	if s.mode == "direct" || id%4 == 0 && s.mode == "rule" {
		outbound = "DIRECT"
		rule = "final => DIRECT"
	}
	connection := &daemon.Connection{
		Id:      fmt.Sprintf("00000000-0000-4000-8000-%012x", id),
		Inbound: "demo-tun", InboundType: "tun", IpVersion: 4,
		Network: profile.network, Protocol: profile.protocol, Domain: profile.domain,
		Source:      fmt.Sprintf("192.0.2.10:%d", 40000+id%20000),
		Destination: "198.51.100.20:443", CreatedAt: now.UnixMilli(),
		Rule: rule, Outbound: outbound,
		ChainList: []string{outbound},
		ProcessInfo: &daemon.ProcessInfo{
			ProcessId: uint32(1000 + id%100), UserId: 1000, UserName: "demo",
			ProcessPath:  "/demo/" + profile.process,
			PackageNames: []string{"example.demo." + profile.process},
		},
	}
	if id%3 == 0 {
		connection.IpVersion = 6
		connection.Source = fmt.Sprintf("[2001:db8::10]:%d", 40000+id%20000)
		connection.Destination = "[2001:db8::20]:443"
	}
	for _, node := range s.outbounds {
		if node.Tag == outbound {
			connection.OutboundType = node.Type
			break
		}
	}
	if outbound != "DIRECT" {
		connection.ChainList = append(connection.ChainList, "Proxy")
	}
	s.connections = append(s.connections, connection)
	s.appendConnectionLogs(connection, uint32(id), now)
}

func (s *service) closeConnection(connection *daemon.Connection, now time.Time) {
	s.appendConnectionClosedLogs(connection, now)
	connection.ClosedAt = now.UnixMilli()
	connection.Uplink = 0
	connection.Downlink = 0
}
