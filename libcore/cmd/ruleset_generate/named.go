package main

import (
	"cmp"
	"slices"
)

type NamedItemList[T any] struct {
	name    string
	content []T
}

// FromMap returns sorted NamedItemList.
func FromMap[T any](source map[string][]T) []*NamedItemList[T] {
	list := make([]*NamedItemList[T], 0, len(source))
	for name, content := range source {
		list = append(list, &NamedItemList[T]{
			name:    name,
			content: content,
		})
	}
	slices.SortFunc(list, func(a, b *NamedItemList[T]) int {
		return cmp.Compare(a.name, b.name)
	})
	return list
}
