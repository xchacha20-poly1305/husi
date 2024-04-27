package libcore

import (
	"runtime/debug"

	"github.com/sagernet/sing-box/common/conntrack"
	"github.com/sagernet/sing-box/log"
)

// SetMemoryLimit sets the limit of libcore.
// Just use it in bg.c
func SetMemoryLimit() {
	const memoryLimit = 45 * 1024 * 1024
	const memoryLimitGo = memoryLimit / 1.5

	debug.SetGCPercent(10)
	debug.SetMemoryLimit(memoryLimitGo)
	conntrack.KillerEnabled = true
	conntrack.MemoryLimit = memoryLimit

	log.Trace("Setted memory limit")
}
