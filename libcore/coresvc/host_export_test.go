package coresvc

import "time"

// CloseWithWatchdogForTest exposes closeWithWatchdog to external tests.
func CloseWithWatchdogForTest(closeFn func() error, timeout time.Duration) error {
	return closeWithWatchdog(closeFn, timeout)
}
