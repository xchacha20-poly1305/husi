package protect

import (
	"testing"

	"github.com/sagernet/sing-box/adapter"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/sys/unix"
)

func boundDevice(t *testing.T, fileDescriptor int) string {
	t.Helper()
	device, err := unix.GetsockoptString(fileDescriptor, unix.SOL_SOCKET, unix.SO_BINDTODEVICE)
	require.NoError(t, err)
	return device
}

func TestNetworkManagerProtectorBindsDefaultInterface(t *testing.T) {
	const loopback = "lo"
	protector := networkManagerProtector{networkManager: &fakeNetworkManager{
		defaultOptions: adapter.NetworkOptions{BindInterface: loopback},
	}}
	fileDescriptor := newUDPSocket(t)

	require.NoError(t, protector.Protect(fileDescriptor))
	assert.Equal(t, loopback, boundDevice(t, fileDescriptor))
}

func TestNetworkManagerProtectorWithoutRoutingLeavesSocketAlone(t *testing.T) {
	protector := networkManagerProtector{networkManager: &fakeNetworkManager{}}
	fileDescriptor := newUDPSocket(t)

	require.NoError(t, protector.Protect(fileDescriptor))
	assert.Empty(t, boundDevice(t, fileDescriptor))
}
