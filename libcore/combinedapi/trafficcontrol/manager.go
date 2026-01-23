package trafficcontrol

import (
	"sync"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing-box/common/compatible"
	"github.com/sagernet/sing/common/observable"
	"github.com/sagernet/sing/common/x/list"

	"github.com/gofrs/uuid/v5"
)

type ConnectionEventType uint8

const (
	ConnectionEventNew ConnectionEventType = iota
	ConnectionEventUpdate
	ConnectionEventClosed
)

type ConnectionEvent struct {
	Type          ConnectionEventType
	ID            uuid.UUID
	Metadata      TrackerMetadata
	UplinkDelta   int64
	DownlinkDelta int64
	ClosedAt      time.Time
}

const closedConnectionsLimit = 1000

type Manager struct {
	uploadTotal   atomic.Int64
	downloadTotal atomic.Int64

	connections             compatible.Map[uuid.UUID, Tracker]
	outboundCounters        compatible.Map[string, *outboundCounter]
	closedConnectionsAccess sync.RWMutex
	closedConnections       list.List[TrackerMetadata]

	eventSubscriber *observable.Subscriber[ConnectionEvent]
}

func NewManager() *Manager {
	return &Manager{}
}

func (m *Manager) SetEventHook(subscriber *observable.Subscriber[ConnectionEvent]) {
	m.eventSubscriber = subscriber
}

func (m *Manager) Join(c Tracker) {
	metadata := c.Metadata()
	m.connections.Store(metadata.ID, c)
	if m.eventSubscriber != nil {
		m.eventSubscriber.Emit(ConnectionEvent{
			Type:     ConnectionEventNew,
			ID:       metadata.ID,
			Metadata: metadata,
		})
	}
}

func (m *Manager) Leave(c Tracker) {
	metadata := c.Metadata()
	_, loaded := m.connections.LoadAndDelete(metadata.ID)
	if !loaded {
		return
	}
	closedAt := time.Now()
	metadata.ClosedAt = closedAt
	m.closedConnectionsAccess.Lock()
	defer m.closedConnectionsAccess.Unlock()
	if m.closedConnections.Len() >= closedConnectionsLimit {
		m.closedConnections.PopFront()
	}
	m.closedConnections.PushBack(metadata)
	if m.eventSubscriber != nil {
		m.eventSubscriber.Emit(ConnectionEvent{
			Type:     ConnectionEventClosed,
			ID:       metadata.ID,
			Metadata: metadata,
			ClosedAt: closedAt,
		})
	}
}

func (m *Manager) PushUploaded(id uuid.UUID, size int64) {
	m.uploadTotal.Add(size)
	if eventSubscriber := m.eventSubscriber; eventSubscriber != nil {
		eventSubscriber.Emit(ConnectionEvent{
			Type:        ConnectionEventUpdate,
			ID:          id,
			UplinkDelta: size,
		})
	}
}

func (m *Manager) PushDownloaded(id uuid.UUID, size int64) {
	m.downloadTotal.Add(size)
	if eventSubscriber := m.eventSubscriber; eventSubscriber != nil {
		eventSubscriber.Emit(ConnectionEvent{
			Type:          ConnectionEventUpdate,
			ID:            id,
			DownlinkDelta: size,
		})
	}
}

func (m *Manager) Total() (up int64, down int64) {
	up = m.uploadTotal.Load()
	down = m.downloadTotal.Load()
	return
}

func (m *Manager) Range(f func(uuid uuid.UUID, metadata Tracker) bool) {
	m.connections.Range(f)
}

func (m *Manager) ClosedConnections() []TrackerMetadata {
	m.closedConnectionsAccess.RLock()
	defer m.closedConnectionsAccess.RUnlock()
	return m.closedConnections.Array()
}

func (m *Manager) Connection(id uuid.UUID) Tracker {
	connection, loaded := m.connections.Load(id)
	if !loaded {
		return nil
	}
	return connection
}

func (m *Manager) ResetStatistic() {
	m.uploadTotal.Store(0)
	m.downloadTotal.Store(0)
}

func (m *Manager) loadOrCreateCounter(tag string) *outboundCounter {
	if traffic, loaded := m.outboundCounters.Load(tag); loaded {
		return traffic
	}
	counter := newOutboundCounter(tag)
	m.outboundCounters.Store(tag, counter)
	return counter
}

func (m *Manager) QueryStats(name string, isUpload bool) int64 {
	counter, loaded := m.outboundCounters.Load(name)
	if !loaded {
		// Not have any connections, so not found counter.
		return 0
	}
	// Use swap to clean old data
	if isUpload {
		return counter.upload.Swap(0)
	}

	return counter.download.Swap(0)
}
