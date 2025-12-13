package ringqueue

import (
	"container/ring"
)

type RingQueue[T any] struct {
	ring *ring.Ring
}

func New[T any](capacity int) *RingQueue[T] {
	return &RingQueue[T]{
		ring: ring.New(capacity),
	}
}

func (q *RingQueue[T]) Add(value T) (front T, popped bool) {
	if q.ring.Value != nil {
		front = q.ring.Value.(T)
		popped = true
	}
	q.ring.Value = value
	q.ring = q.ring.Next()
	return
}

func (q *RingQueue[T]) All() (all []T) {
	q.ring.Do(func(value any) {
		if value != nil {
			all = append(all, value.(T))
		}
	})
	return all
}

func (q *RingQueue[T]) Clear() {
	start := q.ring
	for {
		start.Value = nil
		start = start.Next()
		if start == q.ring {
			break
		}
	}
}
