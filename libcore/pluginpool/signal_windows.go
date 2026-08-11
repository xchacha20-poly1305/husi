//go:build windows

package pluginpool

import (
	"os"
)

func signalTerminate(process *os.Process) error {
	// Windows has no SIGTERM; TerminateProcess is the only portable option.
	return process.Kill()
}
