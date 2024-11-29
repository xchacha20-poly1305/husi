package named

import (
	"cmp"
	"slices"
)

// Named designed to replace map, which is not sorted.
type Named[T any] struct {
	Name    string
	Content T
}

// FromMap returns sorted []Named by name.
func FromMap[T any](source map[string]T) []*Named[T] {
	list := make([]*Named[T], 0, len(source))
	for name, content := range source {
		list = append(list, &Named[T]{
			Name:    name,
			Content: content,
		})
	}
	slices.SortFunc(list, func(a, b *Named[T]) int {
		return cmp.Compare(a.Name, b.Name)
	})
	return list
}
