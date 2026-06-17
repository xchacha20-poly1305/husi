package libcore

import (
	"context"
	"sync"
	"time"

	"github.com/sagernet/sing-box/common/trafficcontrol"

	"github.com/gofrs/uuid/v5"
)

type connectionEventSink func([]ConnectionEvent)

type connectionObserver struct {
	manager *trafficcontrol.Manager

	snapshotAccess sync.Mutex
	snapshots      map[uuid.UUID]connectionSnapshot

	subscriptionAccess sync.RWMutex
	subscriptions      map[int]observerSubscription
	nextID             int

	resetCh chan struct{}
}

const minObserverInterval = 100 * time.Millisecond

type observerSubscription struct {
	sink     connectionEventSink
	interval time.Duration
	lastTick time.Time
}

func newConnectionObserver(manager *trafficcontrol.Manager) *connectionObserver {
	return &connectionObserver{
		manager:       manager,
		snapshots:     make(map[uuid.UUID]connectionSnapshot, 16),
		subscriptions: make(map[int]observerSubscription),
		resetCh:       make(chan struct{}, 1),
	}
}

func (o *connectionObserver) run(ctx context.Context) {
	subscription, done, err := o.manager.SubscribeEvents()
	if err != nil {
		return
	}
	defer o.manager.UnSubscribeEvents(subscription)
	ticker := time.NewTicker(o.effectiveInterval())
	defer ticker.Stop()
	for {
		select {
		case event := <-subscription:
			o.snapshotAccess.Lock()
			var events []ConnectionEvent
			if converted := applyConnectionEvent(event, o.snapshots); converted != nil {
				events = append(events, *converted)
			}
		drain:
			for {
				select {
				case event = <-subscription:
					if converted := applyConnectionEvent(event, o.snapshots); converted != nil {
						events = append(events, *converted)
					}
				default:
					break drain
				}
			}
			o.snapshotAccess.Unlock()
			o.dispatch(events)
		case <-ticker.C:
			o.snapshotAccess.Lock()
			events := buildTrafficUpdates(o.manager, o.snapshots)
			o.snapshotAccess.Unlock()
			o.dispatch(events)
			o.dispatchTick(time.Now())
		case <-o.resetCh:
			ticker.Reset(o.effectiveInterval())
		case <-done:
			return
		case <-ctx.Done():
			return
		}
	}
}

func (o *connectionObserver) register(sink connectionEventSink, interval time.Duration) int {
	o.snapshotAccess.Lock()
	o.subscriptionAccess.Lock()
	id := o.nextID
	o.nextID++
	o.subscriptions[id] = observerSubscription{
		sink:     sink,
		interval: interval,
		lastTick: time.Now(),
	}
	o.subscriptionAccess.Unlock()
	initial := o.snapshotConnectionsLocked()
	o.snapshotAccess.Unlock()
	o.signalReset()
	if len(initial) > 0 {
		sink(initial)
	}
	return id
}

func (o *connectionObserver) unregister(id int) {
	o.subscriptionAccess.Lock()
	delete(o.subscriptions, id)
	o.subscriptionAccess.Unlock()
	o.signalReset()
}

func (o *connectionObserver) updateInterval(id int, interval time.Duration) {
	o.subscriptionAccess.Lock()
	subscription, loaded := o.subscriptions[id]
	if !loaded {
		o.subscriptionAccess.Unlock()
		return
	}
	subscription.interval = interval
	subscription.lastTick = time.Now()
	o.subscriptions[id] = subscription
	o.subscriptionAccess.Unlock()
	o.signalReset()
}

func (o *connectionObserver) effectiveInterval() time.Duration {
	o.subscriptionAccess.RLock()
	defer o.subscriptionAccess.RUnlock()
	var best time.Duration
	for _, subscription := range o.subscriptions {
		if subscription.interval <= 0 {
			continue
		}
		if best == 0 || subscription.interval < best {
			best = subscription.interval
		}
	}
	if best <= 0 {
		best = time.Second
	}
	return max(best, minObserverInterval)
}

func (o *connectionObserver) signalReset() {
	select {
	case o.resetCh <- struct{}{}:
	default:
	}
}

func (o *connectionObserver) dispatch(events []ConnectionEvent) {
	if len(events) == 0 {
		return
	}
	o.subscriptionAccess.RLock()
	sinks := make([]connectionEventSink, 0, len(o.subscriptions))
	for _, subscription := range o.subscriptions {
		sinks = append(sinks, subscription.sink)
	}
	o.subscriptionAccess.RUnlock()
	for _, sink := range sinks {
		sink(events)
	}
}

func (o *connectionObserver) dispatchTick(now time.Time) {
	o.subscriptionAccess.Lock()
	sinks := make([]connectionEventSink, 0, len(o.subscriptions))
	for id, subscription := range o.subscriptions {
		interval := max(subscription.interval, minObserverInterval)
		if subscription.interval <= 0 || now.Sub(subscription.lastTick) < interval {
			continue
		}
		subscription.lastTick = now
		o.subscriptions[id] = subscription
		sinks = append(sinks, subscription.sink)
	}
	o.subscriptionAccess.Unlock()
	for _, sink := range sinks {
		sink([]ConnectionEvent{{Type: ConnectionEventTick}})
	}
}

func (o *connectionObserver) snapshotConnectionsLocked() []ConnectionEvent {
	activeConnections := o.manager.Connections()
	events := make([]ConnectionEvent, 0, len(activeConnections))
	for _, metadata := range activeConnections {
		events = append(events, buildInitialConnectionEvent(metadata, o.snapshots))
	}
	return events
}

func buildInitialConnectionEvent(metadata *trafficcontrol.TrackerMetadata, snapshots map[uuid.UUID]connectionSnapshot) ConnectionEvent {
	snapshot, exists := snapshots[metadata.ID]
	if !exists {
		snapshot = connectionSnapshot{
			uplink:   metadata.Upload.Load(),
			downlink: metadata.Download.Load(),
		}
		snapshots[metadata.ID] = snapshot
	}
	trackerInfo := buildTrackerInfo(metadata)
	trackerInfo.UploadTotal = snapshot.uplink
	trackerInfo.DownloadTotal = snapshot.downlink
	return ConnectionEvent{
		Type:        ConnectionEventNew,
		ID:          metadata.ID.String(),
		TrackerInfo: trackerInfo,
	}
}

type connectionSnapshot struct {
	uplink     int64
	downlink   int64
	hadTraffic bool
}

func applyConnectionEvent(event trafficcontrol.ConnectionEvent, snapshots map[uuid.UUID]connectionSnapshot) *ConnectionEvent {
	switch event.Type {
	case trafficcontrol.ConnectionEventNew:
		if event.Metadata == nil {
			return nil
		}
		if _, exists := snapshots[event.ID]; exists {
			return nil
		}
		snapshots[event.ID] = connectionSnapshot{
			uplink:   event.Metadata.Upload.Load(),
			downlink: event.Metadata.Download.Load(),
		}
		return &ConnectionEvent{
			Type:        ConnectionEventNew,
			ID:          event.ID.String(),
			TrackerInfo: buildTrackerInfo(event.Metadata),
		}
	case trafficcontrol.ConnectionEventClosed:
		delete(snapshots, event.ID)
		closedAt := event.ClosedAt
		if closedAt.IsZero() && event.Metadata != nil && !event.Metadata.ClosedAt.IsZero() {
			closedAt = event.Metadata.ClosedAt
		}
		if closedAt.IsZero() {
			closedAt = time.Now()
		}
		return &ConnectionEvent{
			Type:     ConnectionEventClosed,
			ID:       event.ID.String(),
			ClosedAt: closedAt.Format(time.DateTime),
		}
	default:
		return nil
	}
}

func buildTrafficUpdates(manager *trafficcontrol.Manager, snapshots map[uuid.UUID]connectionSnapshot) []ConnectionEvent {
	activeConnections := manager.Connections()
	activeIndex := make(map[uuid.UUID]*trafficcontrol.TrackerMetadata, len(activeConnections))
	var events []ConnectionEvent
	for _, metadata := range activeConnections {
		activeIndex[metadata.ID] = metadata
		currentUpload := metadata.Upload.Load()
		currentDownload := metadata.Download.Load()
		snapshot, exists := snapshots[metadata.ID]
		if !exists {
			snapshots[metadata.ID] = connectionSnapshot{
				uplink:   currentUpload,
				downlink: currentDownload,
			}
			events = append(events, ConnectionEvent{
				Type:        ConnectionEventNew,
				ID:          metadata.ID.String(),
				TrackerInfo: buildTrackerInfo(metadata),
			})
			continue
		}
		uplinkDelta := currentUpload - snapshot.uplink
		downlinkDelta := currentDownload - snapshot.downlink
		if uplinkDelta < 0 || downlinkDelta < 0 {
			if snapshot.hadTraffic {
				events = append(events, ConnectionEvent{
					Type: ConnectionEventUpdate,
					ID:   metadata.ID.String(),
				})
			}
			snapshot.uplink = currentUpload
			snapshot.downlink = currentDownload
			snapshot.hadTraffic = false
			snapshots[metadata.ID] = snapshot
			continue
		}
		if uplinkDelta > 0 || downlinkDelta > 0 {
			snapshot.uplink = currentUpload
			snapshot.downlink = currentDownload
			snapshot.hadTraffic = true
			snapshots[metadata.ID] = snapshot
			events = append(events, ConnectionEvent{
				Type:          ConnectionEventUpdate,
				ID:            metadata.ID.String(),
				UplinkDelta:   uplinkDelta,
				DownlinkDelta: downlinkDelta,
			})
			continue
		}
		if snapshot.hadTraffic {
			snapshot.hadTraffic = false
			snapshots[metadata.ID] = snapshot
			events = append(events, ConnectionEvent{
				Type: ConnectionEventUpdate,
				ID:   metadata.ID.String(),
			})
		}
	}
	var closedIndex map[uuid.UUID]*trafficcontrol.TrackerMetadata
	for id := range snapshots {
		if _, exists := activeIndex[id]; exists {
			continue
		}
		if closedIndex == nil {
			closedIndex = make(map[uuid.UUID]*trafficcontrol.TrackerMetadata)
			for _, metadata := range manager.ClosedConnections() {
				closedIndex[metadata.ID] = metadata
			}
		}
		closedAt := time.Now()
		if metadata, loaded := closedIndex[id]; loaded && !metadata.ClosedAt.IsZero() {
			closedAt = metadata.ClosedAt
		}
		events = append(events, ConnectionEvent{
			Type:     ConnectionEventClosed,
			ID:       id.String(),
			ClosedAt: closedAt.Format(time.DateTime),
		})
		delete(snapshots, id)
	}
	return events
}
