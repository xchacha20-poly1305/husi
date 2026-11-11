// Package nmvpn configures Linux TUN connections through NetworkManager.
package nmvpn

import (
	"context"
	"sync"

	"github.com/godbus/dbus/v5"
)

// DbusTun is a tun based on a dbus connection.
type DbusTun struct {
	ctx         context.Context
	onLost      func()
	access      sync.RWMutex
	connection  *dbus.Conn
	stopMonitor context.CancelFunc
}

func NewDebugTun(ctx context.Context, onLost func()) *DbusTun {
	return &DbusTun{ctx: ctx, onLost: onLost}
}
