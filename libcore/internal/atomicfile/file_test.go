package atomicfile

import (
	"os"
	"path/filepath"
	"runtime"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestWriteCreatesAndOverwritesFile(t *testing.T) {
	dir := t.TempDir()
	target := filepath.Join(dir, "config.snapshot")

	require.NoError(t, Write(target, []byte("first"), 0o600))
	content, err := os.ReadFile(target)
	require.NoError(t, err)
	require.Equal(t, []byte("first"), content)

	if runtime.GOOS != "windows" {
		info, err := os.Stat(target)
		require.NoError(t, err)
		require.Equal(t, os.FileMode(0o600), info.Mode().Perm())
	}

	require.NoError(t, Write(target, []byte("second"), 0o600))
	content, err = os.ReadFile(target)
	require.NoError(t, err)
	require.Equal(t, []byte("second"), content)

	entries, err := os.ReadDir(dir)
	require.NoError(t, err)
	require.Len(t, entries, 1)
	require.Equal(t, "config.snapshot", entries[0].Name())
}

func TestWriteCleansUpTempFileOnRenameFailure(t *testing.T) {
	dir := t.TempDir()
	// Make the target a non-empty directory so os.Rename fails on all platforms.
	target := filepath.Join(dir, "blocked")
	require.NoError(t, os.Mkdir(target, 0o700))
	require.NoError(t, os.WriteFile(filepath.Join(target, "child"), []byte("x"), 0o600))

	err := Write(target, []byte("data"), 0o600)
	require.Error(t, err)

	entries, err := os.ReadDir(dir)
	require.NoError(t, err)
	require.Len(t, entries, 1)
	require.Equal(t, "blocked", entries[0].Name())
}
