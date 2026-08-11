//go:build !linux && !darwin && !windows

package daemonhost

import (
	"os"
)

func ServiceInstall(workingDir string) error {
	return os.ErrInvalid
}

func ServiceUninstall(workingDir string, purge bool) error {
	return os.ErrInvalid
}

func ServiceStart() error {
	return os.ErrInvalid
}

func ServiceStop() error {
	return os.ErrInvalid
}

func ServiceStatus() (*ServiceStatusResult, error) {
	return nil, os.ErrInvalid
}
