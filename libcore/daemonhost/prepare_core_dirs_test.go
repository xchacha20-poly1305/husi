package daemonhost

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestPrepareCoreDirs(t *testing.T) {
	dir := t.TempDir()
	coreDir, err := prepareCoreDirs(dir)
	require.NoError(t, err)
	assert.Equal(t, filepath.Join(dir, "core"), coreDir)
	for _, name := range []string{"core", "cache"} {
		path := filepath.Join(dir, name)
		info, err := os.Stat(path)
		require.NoError(t, err, "stat %s", name)
		assert.True(t, info.IsDir(), "%s is not a directory", name)
		assert.Equal(t, os.FileMode(0o700), info.Mode().Perm(), "%s mode", name)
	}
}
