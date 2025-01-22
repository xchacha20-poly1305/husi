package libcore

import (
	"github.com/sagernet/sing/common"
)

type StringIterator interface {
	Next() string
	HasNext() bool
	Length() int32
}

var _ StringIterator = (*iterator[string])(nil)

type iterator[T any] struct {
	values []T
}

func newIterator[T any](values []T) *iterator[T] {
	return &iterator[T]{values}
}

func (i *iterator[T]) Next() T {
	if len(i.values) == 0 {
		return common.DefaultValue[T]()
	}
	nextValue := i.values[0]
	i.values = i.values[1:]
	return nextValue
}

func (i *iterator[T]) HasNext() bool {
	return len(i.values) > 0
}

func (i *iterator[T]) Length() int32 {
	return int32(len(i.values))
}

type abstractIterator[T any] interface {
	Next() T
	HasNext() bool
	Length() int32
}

func iteratorToArray[T any](iterator abstractIterator[T]) []T {
	if iterator == nil {
		return nil
	}
	values := make([]T, 0, iterator.Length())
	for iterator.HasNext() {
		values = append(values, iterator.Next())
	}
	return values
}
