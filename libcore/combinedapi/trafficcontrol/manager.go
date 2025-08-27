package trafficcontrol

import (
	"sync"
	"time"

	"github.com/sagernet/sing-box/experimental/clashapi/compatible"
	"github.com/sagernet/sing/common/x/list"

	"github.com/gofrs/uuid/v5"
)

type Manager struct {
	connections             compatible.Map[uuid.UUID, Tracker]
	outboundCounters        compatible.Map[string, *outboundCounter] // use sync.Map to avoid rare race.
	closedConnectionsAccess sync.RWMutex
	closedConnections       list.List[TrackerMetadata]
	// process     *process.Process
}

func NewManager() *Manager {
	return &Manager{}
}

func (m *Manager) Join(c Tracker) {
	m.connections.Store(c.Metadata().ID, c)
}

func (m *Manager) Leave(c Tracker) {
	metadata := c.Metadata()
	_, loaded := m.connections.LoadAndDelete(metadata.ID)
	if loaded {
		metadata.CreatedAt = time.Now()
		m.closedConnectionsAccess.Lock()
		defer m.closedConnectionsAccess.Unlock()
		const maxClosedConnections = 1000
		if m.closedConnections.Len() >= maxClosedConnections {
			m.closedConnections.PopFront()
		}
		m.closedConnections.PushBack(metadata)
	}
}

func (m *Manager) Range(f func(uuid uuid.UUID, tracker Tracker) bool) {
	m.connections.Range(f)
}

func (m *Manager) Connection(id uuid.UUID) Tracker {
	connection, loaded := m.connections.Load(id)
	if !loaded {
		return nil
	}
	return connection
}

func (m *Manager) ClosedConnectionsMetadata() []TrackerMetadata {
	m.closedConnectionsAccess.RLock()
	defer m.closedConnectionsAccess.RUnlock()
	return m.closedConnections.Array()
}

func (m *Manager) loadOrCreateTraffic(tag string) *outboundCounter {
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
		// Not has any connections, so not found counter.
		return 0
	}
	// Use swap to clean old data
	if isUpload {
		return counter.upload.Swap(0)
	} else {
		return counter.download.Swap(0)
	}
}
