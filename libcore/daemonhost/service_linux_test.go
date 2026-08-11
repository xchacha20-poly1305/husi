//go:build linux

package daemonhost

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

const protectedSystemExecutable = "/usr/bin/true"

func TestResolveInstallBinaryUsesProtectedLocationInPlace(t *testing.T) {
	protectedPath := requireProtectedSystemExecutable(t)

	installBin, useInPlace, err := resolveInstallBinary(protectedPath)
	require.NoError(t, err)
	assert.True(t, useInPlace)
	assert.Equal(t, mustResolveExecutablePath(t, protectedPath), installBin)
}

func TestResolveInstallBinaryCopiesFromUserWritableDir(t *testing.T) {
	executablePath := filepath.Join(t.TempDir(), "husi-core")
	require.NoError(t, os.WriteFile(executablePath, []byte("fake"), 0o755))

	installBin, useInPlace, err := resolveInstallBinary(executablePath)
	require.NoError(t, err)
	assert.False(t, useInPlace)
	assert.Equal(t, defaultInstallBin, installBin)
}

func TestResolveInstallBinaryFollowsSymlinkIntoProtectedDir(t *testing.T) {
	protectedPath := requireProtectedSystemExecutable(t)
	linkPath := filepath.Join(t.TempDir(), "husi-core")
	require.NoError(t, os.Symlink(protectedPath, linkPath))

	installBin, useInPlace, err := resolveInstallBinary(linkPath)
	require.NoError(t, err)
	assert.True(t, useInPlace)
	assert.Equal(t, mustResolveExecutablePath(t, protectedPath), installBin)
}

func TestResolveInstallBinaryMissingPath(t *testing.T) {
	_, _, err := resolveInstallBinary(filepath.Join(t.TempDir(), "missing"))
	require.Error(t, err)
}

func TestPrepareInstallDirectoryRejectsUserWritable(t *testing.T) {
	installBin := filepath.Join(t.TempDir(), "husi", "husi-core")
	err := prepareInstallDirectory(installBin)
	require.Error(t, err)
}

func requireProtectedSystemExecutable(t *testing.T) string {
	t.Helper()
	if _, err := os.Stat(protectedSystemExecutable); err != nil {
		t.Skipf("missing %s: %v", protectedSystemExecutable, err)
	}
	if err := ValidateProtectedDirectory(filepath.Dir(protectedSystemExecutable)); err != nil {
		t.Skipf("skip protected %s: %v", filepath.Dir(protectedSystemExecutable), err)
	}
	return protectedSystemExecutable
}

func mustResolveExecutablePath(t *testing.T, path string) string {
	t.Helper()
	resolvedPath, err := resolveExecutablePath(path)
	require.NoError(t, err)
	return resolvedPath
}
