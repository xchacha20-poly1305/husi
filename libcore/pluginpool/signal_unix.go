//go:build unix

package pluginpool

import (
	"os"
	"syscall"
)

func signalTerminate(process *os.Process) error {
	return process.Signal(syscall.SIGTERM)
}
