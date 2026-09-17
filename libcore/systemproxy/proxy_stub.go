//go:build android || !(windows || linux || darwin)

package systemproxy

import (
	"os"
)

func Enable(string, uint16) error {
	return os.ErrInvalid
}

func Disable() error {
	return os.ErrInvalid
}
