package main

import (
	"cmp"
	"slices"
)

type NamedList[T any] struct {
	name    string
	content []T
}

// FromMap returns sorted NamedList.
func FromMap[T any](source map[string][]T) []*NamedList[T] {
	list := make([]*NamedList[T], 0, len(source))
	for name, content := range source {
		list = append(list, &NamedList[T]{
			name:    name,
			content: content,
		})
	}
	slices.SortFunc(list, func(a, b *NamedList[T]) int {
		return cmp.Compare(a.name, b.name)
	})
	return list
}
