package daemonhost

import (
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
)

const testConfig = `{
  "log": {"level": "warn"},
  "outbounds": [{"type": "direct", "tag": "direct"}]
}`

// newRunningDaemonService returns a daemon service with a live instance and a
// grace period short enough to wait out in a test.
func newRunningDaemonService(t *testing.T) *daemonDaemonService {
	t.Helper()
	// The instance may write cache.db into the process working directory.
	t.Chdir(t.TempDir())

	svc := newDaemonDaemonService(t.TempDir(), "test", NewOwnerStore(NewPeerRegistry()))
	svc.clientGrace = 50 * time.Millisecond

	hostCtx := sessionBaseContext(t.Context())
	host, err := coresvc.NewHost(coresvc.HostOptions{
		Context:          hostCtx,
		Version:          "test",
		LogMaxLines:      100,
		BuildEnvironment: "test-env",
	})
	require.NoError(t, err)
	t.Cleanup(func() { _ = host.Close() })
	svc.host = host

	require.NoError(t, host.StartOrReload(t.Context(), testConfig))
	require.True(t, host.HasInstance())
	return svc
}

func TestServiceStopsWhenTheLastClientDetaches(t *testing.T) {
	svc := newRunningDaemonService(t)
	require.NoError(t, SaveSnapshot(svc.workingDir, &Snapshot{Config: testConfig}))
	require.NoError(t, SetWasRunning(svc.workingDir, true))

	svc.clientAttached()
	svc.clientAttached()
	svc.clientDetached()

	time.Sleep(2 * svc.clientGrace)
	require.True(t, svc.host.HasInstance(), "a remaining client keeps the service running")

	svc.clientDetached()
	require.Eventually(t, func() bool {
		return !svc.host.HasInstance()
	}, 5*time.Second, 10*time.Millisecond)

	// Nobody asked for the service to end, so the boot restore keeps its input.
	require.True(t, WasRunning(svc.workingDir))
	snapshot, err := LoadSnapshot(svc.workingDir)
	require.NoError(t, err)
	require.NotNil(t, snapshot)
}

func TestClientReturningWithinGraceKeepsTheService(t *testing.T) {
	svc := newRunningDaemonService(t)

	svc.clientAttached()
	svc.clientDetached()
	svc.clientAttached()

	time.Sleep(4 * svc.clientGrace)
	require.True(t, svc.host.HasInstance(), "a client that came back keeps the service running")
}
