package daemonhost

import (
	"context"
	"io"
	"sync"
	"sync/atomic"
	"testing"
	"testing/synctest"
	"time"

	"github.com/stretchr/testify/require"
)

func TestWatchStdinEOFCancels(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		stdin, closeStdin := blockingStdin(t)
		var parentAlive atomic.Bool
		parentAlive.Store(true)

		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()

		const interval = 50 * time.Millisecond
		StartWatchdog(cancel, WatchdogOptions{
			Stdin:         stdin,
			Getppid:       func() int { return 42 },
			ProcessExists: func(int) bool { return parentAlive.Load() },
			Interval:      interval,
		})

		closeStdin()
		synctest.Wait()
		require.Error(t, ctx.Err(), "context not cancelled after stdin EOF")

		// watchParentPID does not observe cancel; make the parent disappear so
		// the ticker loop can exit before the bubble shuts down.
		parentAlive.Store(false)
		synctest.Sleep(interval)
	})
}

func TestWatchParentPIDChangeCancels(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		stdin, _ := blockingStdin(t)
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()

		var ppid atomic.Int32
		ppid.Store(100)

		const interval = 20 * time.Millisecond
		StartWatchdog(cancel, WatchdogOptions{
			Stdin: stdin,
			Getppid: func() int {
				return int(ppid.Load())
			},
			ProcessExists: func(pid int) bool {
				return pid == int(ppid.Load())
			},
			Interval: interval,
		})

		synctest.Sleep(3 * interval)
		require.NoError(t, ctx.Err(), "cancelled before parent change")

		ppid.Store(1) // reparented to init
		synctest.Sleep(interval)
		require.Error(t, ctx.Err(), "context not cancelled after parent PID change")
	})
}

func TestWatchParentProcessGoneCancels(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		stdin, _ := blockingStdin(t)
		ctx, cancel := context.WithCancel(t.Context())
		defer cancel()

		var alive atomic.Bool
		alive.Store(true)
		const parentPID = 77
		const interval = 20 * time.Millisecond

		StartWatchdog(cancel, WatchdogOptions{
			Stdin:   stdin,
			Getppid: func() int { return parentPID },
			ProcessExists: func(pid int) bool {
				if pid != parentPID {
					return false
				}
				return alive.Load()
			},
			Interval: interval,
		})

		synctest.Sleep(3 * interval)
		require.NoError(t, ctx.Err(), "cancelled while parent alive")

		alive.Store(false)
		synctest.Sleep(interval)
		require.Error(t, ctx.Err(), "context not cancelled after parent process gone")
	})
}

// eofReader blocks on done until it is closed, then returns io.EOF.
// Channel receive is durably blocked, so synctest's fake clock can still advance.
type eofReader struct {
	done <-chan struct{}
}

func (r eofReader) Read([]byte) (int, error) {
	<-r.done
	return 0, io.EOF
}

func blockingStdin(t *testing.T) (io.Reader, func()) {
	t.Helper()
	done := make(chan struct{})
	var once sync.Once
	stop := func() { once.Do(func() { close(done) }) }
	t.Cleanup(stop)
	return eofReader{done: done}, stop
}
