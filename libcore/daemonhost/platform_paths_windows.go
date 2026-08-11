//go:build windows

package daemonhost

import (
	"os"
	"path/filepath"
)

func defaultWorkingDir() string {
	if programData := os.Getenv("ProgramData"); programData != "" {
		return filepath.Join(programData, "husi")
	}
	return `C:\ProgramData\husi`
}

func defaultSocketPath() string {
	return DefaultDaemonPipePath
}
