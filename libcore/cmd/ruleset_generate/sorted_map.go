package main

import (
	"cmp"
	"slices"

	"github.com/sagernet/sing/common/x/collections"
)

type sortedStringMap[V any] map[string]V

type sortedStringMapEntry[V any] collections.MapEntry[string, V]

func (m sortedStringMap[V]) Get(key string) V {
	return m[key]
}

func (m sortedStringMap[V]) Put(key string, value V) {
	m[key] = value
}

func (m sortedStringMap[V]) Remove(key string) {
	delete(m, key)
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
	entries := make([]sortedStringMapEntry[V], 0, len(m))
	for key, value := range m {
		entries = append(entries, sortedStringMapEntry[V]{key, value})
	}
	slices.SortFunc(entries, func(a, b sortedStringMapEntry[V]) int {
		return cmp.Compare(a.Key, b.Key)
	})
	return entries
}
