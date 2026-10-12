//go:build unix

package protect

import (
	"context"
	"path/filepath"
	"testing"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common/control"
	"github.com/sagernet/sing/service"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/pluginoption"
	"github.com/xchacha20-poly1305/protect-go"
	"golang.org/x/sys/unix"
)

type fakeNetworkManager struct {
	adapter.NetworkManager
	defaultOptions adapter.NetworkOptions
}

func (m *fakeNetworkManager) DefaultOptions() adapter.NetworkOptions {
	return m.defaultOptions
}

func (m *fakeNetworkManager) InterfaceFinder() control.InterfaceFinder {
	finder := control.NewDefaultInterfaceFinder()
	_ = finder.Update()
	return finder
}

func (m *fakeNetworkManager) AutoDetectInterface() bool {
	return false
}

func (m *fakeNetworkManager) AutoRedirectOutputMark() uint32 {
	return 0
}

func newUDPSocket(t *testing.T) int {
	t.Helper()
	fileDescriptor, err := unix.Socket(unix.AF_INET, unix.SOCK_DGRAM, 0)
	require.NoError(t, err)
	t.Cleanup(func() { _ = unix.Close(fileDescriptor) })
	return fileDescriptor
}

// startService starts a service whose context provides the given services,
// and returns the path it listens on.
func startService(t *testing.T, options pluginoption.ProtectServiceOptions, register func(ctx context.Context)) string {
	t.Helper()
	ctx := service.ContextWithDefaultRegistry(t.Context())
	register(ctx)
	options.Path = filepath.Join(t.TempDir(), "protect_path")
	protectService, err := NewService(ctx, log.NewNOPFactory().Logger(), "protect", options)
	require.NoError(t, err)
	scope := adapter.NewScope(ctx, log.NewNOPFactory().Logger())
	t.Cleanup(func() { _ = scope.Close() })
	require.NoError(t, protectService.Start(adapter.StartStateStart, scope))
	return options.Path
}

func TestServiceUsesRegisteredProtector(t *testing.T) {
	protected := make(chan uint64, 1)
	path := startService(t, pluginoption.ProtectServiceOptions{}, func(ctx context.Context) {
		service.MustRegister[protect.Protector](ctx, protect.ProtectorFunc(func(fileDescriptor int) error {
			// The server closes its copy once Protect returns.
			var stat unix.Stat_t
			if err := unix.Fstat(fileDescriptor, &stat); err != nil {
				return err
			}
			protected <- stat.Ino
			return nil
		}))
	})

	fileDescriptor := newUDPSocket(t)
	require.NoError(t, protect.Protect(path, fileDescriptor))
	var stat unix.Stat_t
	require.NoError(t, unix.Fstat(fileDescriptor, &stat))
	assert.Equal(t, stat.Ino, <-protected)
}

func TestServiceFallsBackToNetworkManager(t *testing.T) {
	path := startService(t, pluginoption.ProtectServiceOptions{}, func(ctx context.Context) {
		service.MustRegister[adapter.NetworkManager](ctx, &fakeNetworkManager{})
	})

	require.NoError(t, protect.Protect(path, newUDPSocket(t)))
}

func TestNewServiceRequiresProtector(t *testing.T) {
	ctx := service.ContextWithDefaultRegistry(t.Context())
	_, err := NewService(ctx, log.NewNOPFactory().Logger(), "protect", pluginoption.ProtectServiceOptions{Path: "protect_path"})
	require.Error(t, err)
}

func TestServiceVerifyPeerAcceptsOwnProcess(t *testing.T) {
	path := startService(t, pluginoption.ProtectServiceOptions{VerifyPeer: true}, func(ctx context.Context) {
		service.MustRegister[adapter.NetworkManager](ctx, &fakeNetworkManager{})
	})

	require.NoError(t, protect.Protect(path, newUDPSocket(t)))
}
