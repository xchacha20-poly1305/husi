package distro

import (
	_ "os"
	_ "unsafe"

	E "github.com/sagernet/sing/common/exceptions"
)

//go:linkname checkPidfdOnce os.checkPidfdOnce
var checkPidfdOnce func() error

func init() {
	// https://github.com/golang/go/issues/70508
	// Fix exec crash on Android
	checkPidfdOnce = func() error {
		return E.New("pidfd is blocked by the Android seccomp policy")
	}
}
