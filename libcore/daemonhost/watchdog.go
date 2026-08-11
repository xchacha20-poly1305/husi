package daemonhost

import (
	"context"
	"io"
	"os"
	"time"
)

const parentPIDCheckInterval = 2 * time.Second

// WatchdogOptions configures parent-liveness detection for session mode.
// Zero values use process defaults (os.Stdin, os.Getppid, 2s interval).
type WatchdogOptions struct {
	// Stdin is read until EOF; EOF cancels the session. Defaults to os.Stdin.
	Stdin io.Reader
	// Getppid returns the current parent PID. Defaults to os.Getppid.
	Getppid func() int
	// ProcessExists reports whether a PID is still alive. Defaults to the
	// platform processExists helper.
	ProcessExists func(pid int) bool
	// Interval between parent PID checks. Defaults to parentPIDCheckInterval.
	Interval time.Duration
}

// StartWatchdog runs stdin-EOF and parent-PID guards that both call cancel.
// The original parent PID is sampled at start. Callers should cancel the same
// context SessionHost.Run selects on.
func StartWatchdog(cancel context.CancelFunc, opts WatchdogOptions) {
	if cancel == nil {
		return
	}
	stdin := opts.Stdin
	if stdin == nil {
		stdin = os.Stdin
	}
	getppid := opts.Getppid
	if getppid == nil {
		getppid = os.Getppid
	}
	exists := opts.ProcessExists
	if exists == nil {
		exists = processExists
	}
	interval := opts.Interval
	if interval <= 0 {
		interval = parentPIDCheckInterval
	}

	go watchStdin(stdin, cancel)
	go watchParentPID(getppid, exists, getppid(), interval, cancel)
}

func watchStdin(r io.Reader, cancel context.CancelFunc) {
	_, _ = io.Copy(io.Discard, r)
	cancel()
}

func watchParentPID(getppid func() int, exists func(int) bool, originalPID int, interval time.Duration, cancel context.CancelFunc) {
	if originalPID <= 1 {
		// Started by init/system already; parent-pid guard cannot help.
		return
	}
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for range ticker.C {
		current := getppid()
		// Reparented (unix: typically to 1) or parent gone on platforms where
		// Getppid keeps returning the dead PID.
		if current != originalPID || current == 1 || !exists(originalPID) {
			cancel()
			return
		}
	}
}
