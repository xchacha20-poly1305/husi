package syncmap

import "sync"

// RWMutexMap is an implementation of mapInterface using a sync.RWMutex.
type RWMutexMap[K comparable, V any] struct {
	mu    sync.RWMutex
	dirty map[K]V
}

// NewRWMutexMap creates returns a new RWMutexMap.
// It will use dirty directly, if you don't want to change the source,
// you'd better clone a new map.
func NewRWMutexMap[K comparable, V any](dirty map[K]V) *RWMutexMap[K, V] {
	if dirty == nil {
		dirty = make(map[K]V)
	}
	return &RWMutexMap[K, V]{dirty: dirty}
}

func (m *RWMutexMap[K, V]) Load(key K) (value V, ok bool) {
	m.mu.RLock()
	value, ok = m.dirty[key]
	m.mu.RUnlock()
	return
}

func (m *RWMutexMap[K, V]) Store(key K, value V) {
	m.mu.Lock()
	if m.dirty == nil {
		m.dirty = make(map[K]V)
	}
	m.dirty[key] = value
	m.mu.Unlock()
}

func (m *RWMutexMap[K, V]) LoadOrStore(key K, value V) (actual V, loaded bool) {
	m.mu.Lock()
	actual, loaded = m.dirty[key]
	if !loaded {
		actual = value
		if m.dirty == nil {
			m.dirty = make(map[K]V)
		}
		m.dirty[key] = value
	}
	m.mu.Unlock()
	return actual, loaded
}

func (m *RWMutexMap[K, V]) Delete(key K) {
	m.mu.Lock()
	delete(m.dirty, key)
	m.mu.Unlock()
}

func (m *RWMutexMap[K, V]) Range(f func(key K, value V) (shouldContinue bool)) {
	m.mu.RLock()
	for k, v := range m.dirty {
		if !f(k, v) {
			break
		}
	}
	m.mu.RUnlock()
}

func (m *RWMutexMap[K, V]) Len() (length int) {
	m.mu.RLock()
	length = len(m.dirty)
	m.mu.RUnlock()
	return length
}

func (m *RWMutexMap[K, V]) Array() []V {
	m.mu.RLock()
	array := make([]V, 0, len(m.dirty))
	for _, value := range m.dirty {
		array = append(array, value)
	}
	m.mu.RUnlock()
	return array
}
