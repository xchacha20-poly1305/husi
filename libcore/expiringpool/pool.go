package expiringpool

import (
	"container/heap"
	"context"
	"sync"
	"time"

	"github.com/sagernet/sing/common"
)

type item[T any] struct {
	value     T
	expiresAt time.Time
	index     int // heap index
}

type minHeap[T any] []*item[T]

func (h minHeap[T]) Len() int {
	return len(h)
}

func (h minHeap[T]) Less(i, j int) bool {
	return h[i].expiresAt.Before(h[j].expiresAt)
}

func (h minHeap[T]) Swap(i, j int) {
	h[i], h[j] = h[j], h[i]
	h[i].index, h[j].index = i, j
}

func (h *minHeap[T]) Push(x any) {
	it := x.(*item[T])
	it.index = len(*h)
	*h = append(*h, it)
}

func (h *minHeap[T]) Pop() any {
	old := *h
	n := len(old)
	it := old[n-1]
	it.index = -1
	*h = old[0 : n-1]
	return it
}

type ExpiringPool[T comparable] struct {
	ctx     context.Context
	onClean func(T)
	expire  time.Duration

	access sync.Mutex
	heap   minHeap[T]
	items  map[T]*item[T]

	cancel context.CancelFunc
}

func New[T comparable](ctx context.Context, expire time.Duration, onClean func(T)) *ExpiringPool[T] {
	ctx, cancel := context.WithCancel(ctx)
	return &ExpiringPool[T]{
		ctx:     ctx,
		onClean: onClean,
		expire:  expire,
		items:   make(map[T]*item[T]),
		cancel:  cancel,
	}
}

func (e *ExpiringPool[T]) Start() {
	go e.cleanLoop()
}

func (e *ExpiringPool[T]) cleanLoop() {
	for {
		e.access.Lock()
		if len(e.heap) == 0 {
			e.access.Unlock()
			select {
			case <-e.ctx.Done():
				return
			case <-time.After(e.expire):
				continue
			}
		}

		next := e.heap[0]
		now := time.Now()
		wait := next.expiresAt.Sub(now)
		e.access.Unlock()

		if wait > 0 {
			select {
			case <-e.ctx.Done():
				return
			case <-time.After(wait):
				continue
			}
		}

		e.access.Lock()
		// re-check
		if len(e.heap) == 0 || !e.heap[0].expiresAt.Before(time.Now()) {
			e.access.Unlock()
			continue
		}
		it := heap.Pop(&e.heap).(*item[T])
		delete(e.items, it.value)
		e.access.Unlock()

		e.onClean(it.value)
	}
}

func (e *ExpiringPool[T]) Get() T {
	e.access.Lock()
	defer e.access.Unlock()
	if len(e.heap) <= 0 {
		return common.DefaultValue[T]()
	}
	// take oldest
	it := heap.Pop(&e.heap).(*item[T])
	delete(e.items, it.value)
	return it.value
}

func (e *ExpiringPool[T]) Put(value T) {
	e.access.Lock()
	defer e.access.Unlock()
	expiresAt := time.Now().Add(e.expire)
	it := &item[T]{value: value, expiresAt: expiresAt}
	heap.Push(&e.heap, it)
	e.items[value] = it
}

func (e *ExpiringPool[T]) Close() {
	e.access.Lock()
	defer e.access.Unlock()
	if e.cancel != nil {
		e.cancel()
		e.cancel = nil
	}
	// clean remaining
	for len(e.heap) > 0 {
		it := heap.Pop(&e.heap).(*item[T])
		e.onClean(it.value)
	}
}
