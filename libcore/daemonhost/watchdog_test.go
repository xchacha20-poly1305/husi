package daemonhost

import (
	"context"
	"io"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

func TestWatchStdinEOFCancels(t *testing.T) {
	reader, writer := io.Pipe()
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	StartWatchdog(cancel, WatchdogOptions{
		Stdin:         reader,
		Getppid:       func() int { return 42 },
		ProcessExists: func(int) bool { return true },
		Interval:      50 * time.Millisecond,
	})

	require.NoError(t, writer.Close())

	select {
	case <-ctx.Done():
	case <-time.After(2 * time.Second):
		require.FailNow(t, "context not cancelled after stdin EOF")
	}
}

func TestWatchParentPIDChangeCancels(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	var ppid atomic.Int32
	ppid.Store(100)

	StartWatchdog(cancel, WatchdogOptions{
		Stdin: io.NopCloser(nilReader{}),
		Getppid: func() int {
			return int(ppid.Load())
		},
		ProcessExists: func(pid int) bool {
			return pid == int(ppid.Load())
		},
		Interval: 20 * time.Millisecond,
	})

	// Parent still alive: should not cancel yet.
	select {
	case <-ctx.Done():
		require.FailNow(t, "cancelled before parent change")
	case <-time.After(60 * time.Millisecond):
	}

	ppid.Store(1) // reparented to init

	select {
	case <-ctx.Done():
	case <-time.After(2 * time.Second):
		require.FailNow(t, "context not cancelled after parent PID change")
	}
}

func TestWatchParentProcessGoneCancels(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	defer cancel()

	alive := atomic.Bool{}
	alive.Store(true)
	const parentPID = 77

	StartWatchdog(cancel, WatchdogOptions{
		Stdin:   io.NopCloser(nilReader{}),
		Getppid: func() int { return parentPID },
		ProcessExists: func(pid int) bool {
			if pid != parentPID {
				return false
			}
			return alive.Load()
		},
		Interval: 20 * time.Millisecond,
	})

	select {
	case <-ctx.Done():
		require.FailNow(t, "cancelled while parent alive")
	case <-time.After(60 * time.Millisecond):
	}

	alive.Store(false)

	select {
	case <-ctx.Done():
	case <-time.After(2 * time.Second):
		require.FailNow(t, "context not cancelled after parent process gone")
	}
}

// nilReader never returns so the stdin watchdog stays quiet in parent-pid tests.
type nilReader struct{}

func (nilReader) Read([]byte) (int, error) {
	select {}
}
