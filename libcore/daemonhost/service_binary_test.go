package daemonhost

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestInstallBinaryStopsBeforeReplacing(t *testing.T) {
	sourceDir := t.TempDir()
	destinationDir := t.TempDir()
	source := filepath.Join(sourceDir, "husi-core")
	destination := filepath.Join(destinationDir, "bin", "husi-core")
	require.NoError(t, os.WriteFile(source, []byte("new"), 0o755))
	require.NoError(t, os.MkdirAll(filepath.Dir(destination), 0o755))
	require.NoError(t, os.WriteFile(destination, []byte("old"), 0o755))

	stopped := false
	err := installBinary(source, destination, func() error {
		content, err := os.ReadFile(destination)
		require.NoError(t, err)
		assert.Equal(t, []byte("old"), content)
		stopped = true
		return nil
	})
	require.NoError(t, err)
	assert.True(t, stopped)

	content, err := os.ReadFile(destination)
	require.NoError(t, err)
	assert.Equal(t, []byte("new"), content)
}

func TestInstallBinaryMissingSource(t *testing.T) {
	destination := filepath.Join(t.TempDir(), "husi-core")
	err := installBinary(filepath.Join(t.TempDir(), "husi-core"), destination, nil)
	require.Error(t, err)
	_, err = os.Stat(destination)
	assert.True(t, os.IsNotExist(err))
}

func TestRemoveBinary(t *testing.T) {
	path := filepath.Join(t.TempDir(), "husi-core")
	require.NoError(t, os.WriteFile(path, []byte("binary"), 0o755))

	require.NoError(t, removeBinary(path))
	_, err := os.Stat(path)
	assert.True(t, os.IsNotExist(err))
	// Removing again is not an error.
	require.NoError(t, removeBinary(path))
}
