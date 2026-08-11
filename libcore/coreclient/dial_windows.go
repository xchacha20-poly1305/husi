//go:build windows

package coreclient

import (
	"context"
	"net"

	"libcore/coresvc"

	"github.com/tailscale/go-winio"
)

func grpcTarget(endpoint string) string {
	if coresvc.IsWindowsPipePath(endpoint) {
		// Avoid the DNS resolver; the context dialer owns the real connection.
		return "passthrough:///husi-daemon-pipe"
	}
	return "unix:" + endpoint
}

func contextDialer(endpoint string) func(context.Context, string) (net.Conn, error) {
	if coresvc.IsWindowsPipePath(endpoint) {
		return func(ctx context.Context, _ string) (net.Conn, error) {
			return winio.DialPipeContext(ctx, endpoint)
		}
	}
	return func(ctx context.Context, _ string) (net.Conn, error) {
		var dialer net.Dialer
		return dialer.DialContext(ctx, "unix", endpoint)
	}
}
