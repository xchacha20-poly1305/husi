// Package trackerchain allow you to use multiple tracker at the same time.
package trackerchain

import (
	"context"
	"net"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing/common"
	N "github.com/sagernet/sing/common/network"
)

var _ adapter.ConnectionTracker = (*Chain)(nil)

// Chain is a chain to link trackers.
type Chain struct {
	trackers []adapter.ConnectionTracker
}

func New(trackers ...adapter.ConnectionTracker) *Chain {
	return &Chain{
		trackers: common.Filter(trackers, func(it adapter.ConnectionTracker) bool {
			return it != nil
		}),
	}
}

func (c *Chain) RoutedConnection(ctx context.Context, conn net.Conn, metadata adapter.InboundContext, matchedRule adapter.Rule, matchOutbound adapter.Outbound) net.Conn {
	for _, tracker := range c.trackers {
		conn = tracker.RoutedConnection(ctx, conn, metadata, matchedRule, matchOutbound)
	}
	return conn
}

func (c *Chain) RoutedPacketConnection(ctx context.Context, conn N.PacketConn, metadata adapter.InboundContext, matchedRule adapter.Rule, matchOutbound adapter.Outbound) N.PacketConn {
	for _, tracker := range c.trackers {
		conn = tracker.RoutedPacketConnection(ctx, conn, metadata, matchedRule, matchOutbound)
	}
	return conn
}

func (c *Chain) Upstream() any {
	return c.trackers
}
