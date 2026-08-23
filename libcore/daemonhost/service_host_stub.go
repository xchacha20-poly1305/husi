//go:build !windows

package daemonhost

import "context"

func runDaemonHost(ctx context.Context, host *DaemonHost) error {
	return host.run(ctx)
}
