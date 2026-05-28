package main

import (
	"slices"

	"github.com/sagernet/sing/common/x/collections"
)

type sortedStringMap[V any] map[string]V

type sortedStringMapEntry[V any] collections.MapEntry[string, V]

func (m sortedStringMap[V]) Get(key string) (V, bool) {
	value, loaded := m[key]
	return value, loaded
}

func (m sortedStringMap[V]) Put(key string, value V) {
	m[key] = value
}

func (m sortedStringMap[V]) Remove(key string) bool {
	if _, loaded := m[key]; !loaded {
		return false
	}
	delete(m, key)
	return true
}

func (m sortedStringMap[V]) Keys() []string {
	keys := make([]string, 0, len(m))
	for key := range m {
		keys = append(keys, key)
	}
	slices.Sort(keys)
	return keys
}

func (m sortedStringMap[V]) Entries() []sortedStringMapEntry[V] {
	keys := m.Keys()
	entries := make([]sortedStringMapEntry[V], 0, len(keys))
	for _, key := range keys {
		entries = append(entries, sortedStringMapEntry[V]{
			Key:   key,
			Value: m[key],
		})
	}
	return entries
}
