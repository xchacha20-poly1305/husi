package trafficcontrol

import (
	"net"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/atomic"
	"github.com/sagernet/sing/common/bufio"
	N "github.com/sagernet/sing/common/network"

	"github.com/gofrs/uuid/v5"
)

type outboundCounter struct {
	tag              string
	upload, download *atomic.Int64
}

func newOutboundCounter(tag string) *outboundCounter {
	return &outboundCounter{
		tag:      tag,
		upload:   new(atomic.Int64),
		download: new(atomic.Int64),
	}
}

type TrackerMetadata struct {
	ID           uuid.UUID
	Metadata     adapter.InboundContext
	CreatedAt    time.Time
	ClosedAt     time.Time
	Upload       *atomic.Int64
	Download     *atomic.Int64
	Chain        []string
	Rule         adapter.Rule
	Outbound     string
	OutboundType string
}

type Tracker interface {
	Metadata() TrackerMetadata
	Close() error
}

type TCPConn struct {
	N.ExtendedConn
	metadata TrackerMetadata
	manager  *Manager
}

func (t *TCPConn) Metadata() TrackerMetadata {
	return t.metadata
}

func (t *TCPConn) Close() error {
	t.manager.Leave(t)
	return t.ExtendedConn.Close()
}

func (t *TCPConn) Upstream() any {
	return t.ExtendedConn
}

func (t *TCPConn) ReaderReplaceable() bool {
	return true
}

func (t *TCPConn) WriterReplaceable() bool {
	return true
}

func NewTCPTracker(conn net.Conn, manager *Manager, metadata adapter.InboundContext, outboundManager adapter.OutboundManager, matchRule adapter.Rule, matchOutbound adapter.Outbound) *TCPConn {
	id, _ := uuid.NewV4()
	var (
		chain        []string
		next         string
		outbound     string
		outboundType string
		counter      *outboundCounter
	)
	if matchOutbound != nil {
		next = matchOutbound.Tag()
		counter = manager.loadOrCreateTraffic(next)
	} else {
		next = outboundManager.Default().Tag()
	}
	for {
		detour, loaded := outboundManager.Outbound(next)
		if !loaded {
			break
		}
		chain = append(chain, next)
		outbound = detour.Tag()
		outboundType = detour.Type()
		group, isGroup := detour.(adapter.OutboundGroup)
		if !isGroup {
			break
		}
		next = group.Now()
	}
	upload := new(atomic.Int64)
	download := new(atomic.Int64)
	tracker := &TCPConn{
		ExtendedConn: bufio.NewCounterConn(conn, []N.CountFunc{func(n int64) {
			upload.Add(n)
			if counter != nil {
				counter.upload.Add(n)
			}
			// manager.PushUploaded(n)
		}}, []N.CountFunc{func(n int64) {
			download.Add(n)
			if counter != nil {
				counter.download.Add(n)
			}
			// manager.PushDownloaded(n)
		}}),
		metadata: TrackerMetadata{
			ID:           id,
			Metadata:     metadata,
			CreatedAt:    time.Now(),
			Upload:       upload,
			Download:     download,
			Chain:        common.Reverse(chain),
			Rule:         matchRule,
			Outbound:     outbound,
			OutboundType: outboundType,
		},
		manager: manager,
	}
	manager.Join(tracker)
	return tracker
}

type UDPConn struct {
	N.PacketConn `json:"-"`
	metadata     TrackerMetadata
	manager      *Manager
}

func (u *UDPConn) Metadata() TrackerMetadata {
	return u.metadata
}

func (u *UDPConn) Close() error {
	u.manager.Leave(u)
	return u.PacketConn.Close()
}

func (u *UDPConn) Upstream() any {
	return u.PacketConn
}

func (u *UDPConn) ReaderReplaceable() bool {
	return true
}

func (u *UDPConn) WriterReplaceable() bool {
	return true
}

func NewUDPTracker(conn N.PacketConn, manager *Manager, metadata adapter.InboundContext, outboundManager adapter.OutboundManager, matchRule adapter.Rule, matchOutbound adapter.Outbound) *UDPConn {
	id, _ := uuid.NewV4()
	var (
		chain        []string
		next         string
		outbound     string
		outboundType string
		counter      *outboundCounter
	)
	if matchOutbound != nil {
		next = matchOutbound.Tag()
		counter = manager.loadOrCreateTraffic(next)
	} else {
		next = outboundManager.Default().Tag()
	}
	for {
		detour, loaded := outboundManager.Outbound(next)
		if !loaded {
			break
		}
		chain = append(chain, next)
		outbound = detour.Tag()
		outboundType = detour.Type()
		group, isGroup := detour.(adapter.OutboundGroup)
		if !isGroup {
			break
		}
		next = group.Now()
	}
	upload := new(atomic.Int64)
	download := new(atomic.Int64)
	trackerConn := &UDPConn{
		PacketConn: bufio.NewCounterPacketConn(conn, []N.CountFunc{func(n int64) {
			upload.Add(n)
			if counter != nil {
				counter.upload.Add(n)
			}
			// manager.PushUploaded(n)
		}}, []N.CountFunc{func(n int64) {
			download.Add(n)
			if counter != nil {
				counter.download.Add(n)
			}
			// manager.PushDownloaded(n)
		}}),
		metadata: TrackerMetadata{
			ID:           id,
			Metadata:     metadata,
			CreatedAt:    time.Now(),
			Upload:       upload,
			Download:     download,
			Chain:        common.Reverse(chain),
			Rule:         matchRule,
			Outbound:     outbound,
			OutboundType: outboundType,
		},
		manager: manager,
	}
	manager.Join(trackerConn)
	return trackerConn
}
