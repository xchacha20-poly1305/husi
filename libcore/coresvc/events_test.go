package coresvc

import (
	"testing"
	"time"

	"libcore/pb/husi/v1"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestEventBroadcasterReplayAndFanout(t *testing.T) {
	b := newEventBroadcaster()
	t.Cleanup(b.Close)

	state := &husiv1.SubscribeServiceEventsResponse{
		Event: &husiv1.SubscribeServiceEventsResponse_State{
			State: &husiv1.ServiceStateUpdate{
				State:       husiv1.ServiceRunState_SERVICE_RUN_STATE_CONNECTED,
				ProfileName: "home",
			},
		},
	}
	speed := &husiv1.SubscribeServiceEventsResponse{
		Event: &husiv1.SubscribeServiceEventsResponse_Speed{
			Speed: &husiv1.SpeedUpdate{
				TxRateProxy: 100,
				RxRateProxy: 200,
			},
		},
	}
	b.Publish(state)
	b.Publish(speed)

	ch1, unsub1 := b.Subscribe()
	t.Cleanup(unsub1)
	ch2, unsub2 := b.Subscribe()
	t.Cleanup(unsub2)

	// Replay: both subscribers get state then speed immediately.
	for i, ch := range []<-chan *husiv1.SubscribeServiceEventsResponse{ch1, ch2} {
		gotState := recvEvent(t, ch, "state replay sub"+string(rune('1'+i)))
		assert.Equal(t, "home", gotState.GetState().GetProfileName())
		gotSpeed := recvEvent(t, ch, "speed replay sub"+string(rune('1'+i)))
		assert.Equal(t, int64(100), gotSpeed.GetSpeed().GetTxRateProxy())
	}

	alert := &husiv1.SubscribeServiceEventsResponse{
		Event: &husiv1.SubscribeServiceEventsResponse_Alert{
			Alert: &husiv1.ServiceAlert{
				Kind:    husiv1.AlertKind_ALERT_KIND_COMMON,
				Message: "boom",
			},
		},
	}
	b.Publish(alert)

	// Live fan-out: both subscribers receive the alert; neither steals from the other.
	for i, ch := range []<-chan *husiv1.SubscribeServiceEventsResponse{ch1, ch2} {
		got := recvEvent(t, ch, "alert sub"+string(rune('1'+i)))
		assert.Equal(t, "boom", got.GetAlert().GetMessage())
	}
}

func TestEventBroadcasterUnsubscribe(t *testing.T) {
	b := newEventBroadcaster()
	t.Cleanup(b.Close)

	ch, unsub := b.Subscribe()
	unsub()

	// Channel must be closed after unsubscribe (no leak / no hang).
	select {
	case _, ok := <-ch:
		assert.False(t, ok, "expected closed channel after unsubscribe")
	case <-time.After(time.Second):
		require.FailNow(t, "timed out waiting for channel close after unsubscribe")
	}

	// Second unsubscribe is a no-op.
	unsub()
}

func TestEventBroadcasterAlertsNotReplayed(t *testing.T) {
	b := newEventBroadcaster()
	t.Cleanup(b.Close)

	b.Publish(&husiv1.SubscribeServiceEventsResponse{
		Event: &husiv1.SubscribeServiceEventsResponse_Alert{
			Alert: &husiv1.ServiceAlert{
				Kind:    husiv1.AlertKind_ALERT_KIND_COMMON,
				Message: "one-shot",
			},
		},
	})

	ch, unsub := b.Subscribe()
	t.Cleanup(unsub)

	select {
	case ev := <-ch:
		require.FailNow(t, "unexpected replay of alert", "%v", ev)
	case <-time.After(50 * time.Millisecond):
		// expected: alerts are not retained
	}
}

func recvEvent(t *testing.T, ch <-chan *husiv1.SubscribeServiceEventsResponse, label string) *husiv1.SubscribeServiceEventsResponse {
	t.Helper()
	select {
	case ev, ok := <-ch:
		require.True(t, ok, "%s: channel closed", label)
		return ev
	case <-time.After(time.Second):
		require.FailNow(t, label+": timed out waiting for event")
		return nil
	}
}
