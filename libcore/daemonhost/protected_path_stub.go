//go:build !linux && !darwin && !windows

package daemonhost

import E "github.com/sagernet/sing/common/exceptions"

func validateProtectedDirectory(directory string) error {
	return E.New("protected directory validation is not supported on this platform")
}
