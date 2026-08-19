package daemonhost

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
)

func TestSnapshotRoundTrip(t *testing.T) {
	dir := t.TempDir()
	snap := &Snapshot{
		Config: `{"inbounds":[]}`,
		Plugins: []*husiv1.PluginProcessSpec{
			{
				Name:    "naive",
				Command: []string{"/bin/true"},
				Environment: map[string]string{
					"FOO": "bar",
				},
				Files: []*husiv1.PluginFile{
					{Name: "cfg.json", Content: []byte(`{}`)},
				},
			},
		},
		ClientMetadata: &husiv1.ClientMetadata{
			ProfileId:   7,
			ProfileName: "home",
		},
	}
	require.NoError(t, SaveSnapshot(dir, snap))
	loaded, err := LoadSnapshot(dir)
	require.NoError(t, err)
	require.NotNil(t, loaded)
	assert.Equal(t, snap.Config, loaded.Config)
	require.Len(t, loaded.Plugins, 1)
	assert.Equal(t, "naive", loaded.Plugins[0].GetName())
	assert.Equal(t, int64(7), loaded.ClientMetadata.GetProfileId())

	require.NoError(t, ClearSnapshot(dir))
	loaded, err = LoadSnapshot(dir)
	require.NoError(t, err)
	assert.Nil(t, loaded)
}

func TestWasRunningAndStartAtBoot(t *testing.T) {
	dir := t.TempDir()
	assert.False(t, WasRunning(dir), "expected not running")
	require.NoError(t, SetWasRunning(dir, true))
	assert.True(t, WasRunning(dir), "expected running")
	require.NoError(t, SetWasRunning(dir, false))
	assert.False(t, WasRunning(dir), "expected cleared")

	assert.False(t, StartAtBoot(dir), "expected start_at_boot off")
	require.NoError(t, SetStartAtBoot(dir, true))
	assert.True(t, StartAtBoot(dir), "expected start_at_boot on")
}

func TestOwnerStateRoundTrip(t *testing.T) {
	dir := t.TempDir()
	identity := PeerIdentity{UID: 0, GID: 0, Username: "root"}
	require.NoError(t, SaveOwnerState(dir, identity))
	loaded, err := LoadOwnerState(dir)
	require.NoError(t, err)
	require.NotNil(t, loaded)
	assert.Equal(t, "root", loaded.Username)
	assert.Equal(t, uint32(0), loaded.UID)
	// Atomic write leaves no tmp files.
	entries, err := os.ReadDir(dir)
	require.NoError(t, err)
	for _, e := range entries {
		if filepath.Ext(e.Name()) == "" && len(e.Name()) > 4 && e.Name()[:4] == ".tmp" {
			assert.Fail(t, "leftover tmp", "%s", e.Name())
		}
	}
}

func TestAtomicWriteFile(t *testing.T) {
	dir := t.TempDir()
	path := filepath.Join(dir, "data.txt")
	require.NoError(t, atomicWriteFile(path, []byte("hello"), 0o600))
	data, err := os.ReadFile(path)
	require.NoError(t, err)
	assert.Equal(t, "hello", string(data))
}
