//go:build unix

package coreclient

import (
	"context"
	"net"
)

func grpcTarget(endpoint string) string {
	return "unix:" + endpoint
}

func contextDialer(endpoint string) func(context.Context, string) (net.Conn, error) {
	return func(ctx context.Context, _ string) (net.Conn, error) {
		var dialer net.Dialer
		return dialer.DialContext(ctx, "unix", endpoint)
	}
}
