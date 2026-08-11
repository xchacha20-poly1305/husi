package coresvc

import (
	"sync"

	"libcore/pb/husi/v1"
)

const eventSubscriberBuffer = 16

// eventBroadcaster fans service-lifecycle events out to SubscribeServiceEvents
// clients. The last state and last speed are retained for replay on subscribe;
// alerts are one-shot and not retained.
type eventBroadcaster struct {
	access      sync.Mutex
	subscribers map[chan *husiv1.SubscribeServiceEventsResponse]struct{}
	lastState   *husiv1.SubscribeServiceEventsResponse
	lastSpeed   *husiv1.SubscribeServiceEventsResponse
	closed      bool
}

func newEventBroadcaster() *eventBroadcaster {
	return &eventBroadcaster{
		subscribers: make(map[chan *husiv1.SubscribeServiceEventsResponse]struct{}),
	}
}

func (b *eventBroadcaster) Publish(event *husiv1.SubscribeServiceEventsResponse) {
	if event == nil {
		return
	}
	b.access.Lock()
	defer b.access.Unlock()
	if b.closed {
		return
	}
	switch event.GetEvent().(type) {
	case *husiv1.SubscribeServiceEventsResponse_State:
		b.lastState = event
	case *husiv1.SubscribeServiceEventsResponse_Speed:
		b.lastSpeed = event
	}
	for ch := range b.subscribers {
		select {
		case ch <- event:
		default:
			// Drop: a slow client must not stall the publisher.
		}
	}
}

func (b *eventBroadcaster) Subscribe() (<-chan *husiv1.SubscribeServiceEventsResponse, func()) {
	ch := make(chan *husiv1.SubscribeServiceEventsResponse, eventSubscriberBuffer)
	b.access.Lock()
	if b.closed {
		b.access.Unlock()
		close(ch)
		return ch, func() {}
	}
	if b.lastState != nil {
		ch <- b.lastState
	}
	if b.lastSpeed != nil {
		ch <- b.lastSpeed
	}
	b.subscribers[ch] = struct{}{}
	b.access.Unlock()

	var once sync.Once
	unsubscribe := func() {
		once.Do(func() {
			b.access.Lock()
			if _, ok := b.subscribers[ch]; ok {
				delete(b.subscribers, ch)
				close(ch)
			}
			b.access.Unlock()
		})
	}
	return ch, unsubscribe
}

func (b *eventBroadcaster) Close() {
	b.access.Lock()
	defer b.access.Unlock()
	if b.closed {
		return
	}
	b.closed = true
	for ch := range b.subscribers {
		close(ch)
	}
	b.subscribers = nil
}
