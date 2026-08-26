//go:build linux

package daemonhost

import (
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/unix"
)

func dropAmbientCapabilities() error {
	err := unix.Prctl(unix.PR_CAP_AMBIENT, unix.PR_CAP_AMBIENT_CLEAR_ALL, 0, 0, 0)
	if err != nil {
		return E.Cause(err, "clear ambient capabilities")
	}
	return nil
}

func hasNetworkAdminCapability() bool {
	header := unix.CapUserHeader{
		Version: unix.LINUX_CAPABILITY_VERSION_3,
	}
	var payload [2]unix.CapUserData
	err := unix.Capget(&header, &payload[0])
	if err != nil {
		return false
	}
	const capabilityBits = 32
	index := unix.CAP_NET_ADMIN / capabilityBits
	mask := uint32(1) << (unix.CAP_NET_ADMIN % capabilityBits)
	return payload[index].Effective&mask != 0
}
