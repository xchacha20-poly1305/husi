package trafficcontrol

import (
	"libcore/syncmap"

	"github.com/gofrs/uuid/v5"
)

type Manager struct {
	connections      syncmap.RWMutexMap[uuid.UUID, Tracker]
	outboundCounters map[string]*outboundCounter
	// closedConnectionsAccess sync.Mutex
	// closedConnections       list.List[TrackerMetadata]
	// process     *process.Process
}

func NewManager() *Manager {
	return &Manager{
		connections:      *syncmap.NewRWMutexMap[uuid.UUID, Tracker](nil),
		outboundCounters: make(map[string]*outboundCounter),
	}
}

func (m *Manager) Join(c Tracker) {
	m.connections.Store(c.Metadata().ID, c)
}

func (m *Manager) Leave(c Tracker) {
	metadata := c.Metadata()
	m.connections.Delete(metadata.ID)
}

func (m *Manager) Connections() []Tracker {
	return m.connections.Array()
}

func (m *Manager) Connection(id uuid.UUID) Tracker {
	connection, loaded := m.connections.Load(id)
	if !loaded {
		return nil
	}
	return connection
}

func (m *Manager) loadOrCreateTraffic(tag string) *outboundCounter {
	if traffic, loaded := m.outboundCounters[tag]; loaded {
		return traffic
	}
	counter := newOutboundCounter(tag)
	m.outboundCounters[tag] = counter
	return counter
	// We not add code about sync now, which may be added when we get race panic.
}

func (m *Manager) QueryStats(name string, isUpload bool) int64 {
	counter, loaded := m.outboundCounters[name]
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
