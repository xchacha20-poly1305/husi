//go:build linux

package daemonhost

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/sys/unix"
)

func TestDropAmbientCapabilitiesKeepsEffectiveSet(t *testing.T) {
	before := effectiveCapabilities(t)

	require.NoError(t, dropAmbientCapabilities())

	assert.Equal(t, before, effectiveCapabilities(t), "clearing the ambient set must not disarm the daemon")
	for _, capability := range []uintptr{unix.CAP_NET_ADMIN, unix.CAP_SYS_PTRACE} {
		raised, err := unix.PrctlRetInt(unix.PR_CAP_AMBIENT, unix.PR_CAP_AMBIENT_IS_SET, capability, 0, 0)
		require.NoError(t, err)
		assert.Zero(t, raised, "no capability may cross execve into a plugin child")
	}
}

func effectiveCapabilities(t *testing.T) [2]uint32 {
	t.Helper()
	header := unix.CapUserHeader{
		Version: unix.LINUX_CAPABILITY_VERSION_3,
	}
	var payload [2]unix.CapUserData
	require.NoError(t, unix.Capget(&header, &payload[0]))
	return [2]uint32{payload[0].Effective, payload[1].Effective}
}

func TestHasNetworkAdminCapabilityMatchesEuid(t *testing.T) {
	if unix.Geteuid() != 0 {
		assert.False(t, hasNetworkAdminCapability())
		return
	}
	assert.True(t, hasNetworkAdminCapability())
}
