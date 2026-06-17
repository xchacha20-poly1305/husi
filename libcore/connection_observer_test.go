package libcore

import (
	"sync/atomic"
	"testing"
	"time"

	"github.com/sagernet/sing-box/common/trafficcontrol"

	"github.com/gofrs/uuid/v5"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestBuildInitialConnectionEventUsesSnapshotBaseline(t *testing.T) {
	id, err := uuid.NewV4()
	require.NoError(t, err)
	upload := new(atomic.Int64)
	download := new(atomic.Int64)
	upload.Store(150)
	download.Store(90)
	snapshots := map[uuid.UUID]connectionSnapshot{
		id: {
			uplink:   100,
			downlink: 40,
		},
	}

	event := buildInitialConnectionEvent(&trafficcontrol.TrackerMetadata{
		ID:       id,
		Upload:   upload,
		Download: download,
	}, snapshots)

	assert.EqualValues(t, 100, event.TrackerInfo.UploadTotal)
	assert.EqualValues(t, 40, event.TrackerInfo.DownloadTotal)
}

func TestConnectionObserverDispatchTickRespectsSubscriberIntervals(t *testing.T) {
	now := time.Now()
	observer := &connectionObserver{
		subscriptions: map[int]observerSubscription{
			1: {
				sink:     func([]ConnectionEvent) {},
				interval: 100 * time.Millisecond,
				lastTick: now.Add(-100 * time.Millisecond),
			},
			2: {
				sink:     func([]ConnectionEvent) {},
				interval: time.Second,
				lastTick: now.Add(-100 * time.Millisecond),
			},
		},
	}
	var fastTicks atomic.Int64
	var slowTicks atomic.Int64
	fastSubscription := observer.subscriptions[1]
	fastSubscription.sink = func(events []ConnectionEvent) {
		require.Len(t, events, 1)
		assert.Equal(t, ConnectionEventTick, events[0].Type)
		fastTicks.Add(1)
	}
	observer.subscriptions[1] = fastSubscription
	slowSubscription := observer.subscriptions[2]
	slowSubscription.sink = func(events []ConnectionEvent) {
		require.Len(t, events, 1)
		assert.Equal(t, ConnectionEventTick, events[0].Type)
		slowTicks.Add(1)
	}
	observer.subscriptions[2] = slowSubscription

	observer.dispatchTick(now)

	assert.EqualValues(t, 1, fastTicks.Load())
	assert.Zero(t, slowTicks.Load())

	observer.dispatchTick(now.Add(900 * time.Millisecond))

	assert.EqualValues(t, 1, slowTicks.Load())
}
