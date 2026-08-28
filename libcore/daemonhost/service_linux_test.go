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

func TestRenderServiceUnitUnprivileged(t *testing.T) {
	unit := renderServiceUnit(serviceUnitOptions{
		ExecPath:   defaultInstallBin,
		WorkingDir: DefaultWorkingDir(),
		SocketPath: DefaultDaemonSocketPath,
		User:       daemonUserName,
	})

	assert.Contains(t, unit, "ExecStart="+defaultInstallBin+" run --dir "+DefaultWorkingDir()+" --socket "+DefaultDaemonSocketPath)
	assert.Contains(t, unit, "User="+daemonUserName)
	assert.Contains(t, unit, "Group="+daemonUserName)
	assert.Contains(t, unit, "AmbientCapabilities="+daemonCapabilities)
	assert.Contains(t, unit, "CapabilityBoundingSet="+daemonCapabilities)
	assert.Contains(t, unit, "DeviceAllow=/dev/net/tun rw")
	assert.Contains(t, unit, "NoNewPrivileges=yes")
	// Process routing rules walk /proc; hiding it would break them silently.
	assert.NotContains(t, unit, "ProtectProc=")
	// The config names the owner's rule sets by absolute path, so an empty
	// /home would fail every start with "no such file or directory".
	assert.NotContains(t, unit, "ProtectHome=")
	assert.NotContains(t, unit, "ReadWritePaths=")
	assert.Contains(t, unit, "StateDirectoryMode=0700")
	assert.Contains(t, unit, "WantedBy=multi-user.target")
}

func TestRenderServiceUnitCustomWorkingDirStaysWritable(t *testing.T) {
	const customDir = "/opt/husi-state"
	unit := renderServiceUnit(serviceUnitOptions{
		ExecPath:   defaultInstallBin,
		WorkingDir: customDir,
		SocketPath: DefaultDaemonSocketPath,
		User:       daemonUserName,
	})

	assert.Contains(t, unit, "ProtectSystem=strict")
	assert.Contains(t, unit, "ReadWritePaths="+customDir)
}

func TestRenderServiceUnitRootFallback(t *testing.T) {
	unit := renderServiceUnit(serviceUnitOptions{
		ExecPath:   defaultInstallBin,
		WorkingDir: DefaultWorkingDir(),
		SocketPath: DefaultDaemonSocketPath,
	})

	assert.NotContains(t, unit, "User=")
	assert.NotContains(t, unit, "AmbientCapabilities=")
	assert.NotContains(t, unit, "ProtectSystem=")
	assert.Contains(t, unit, "StateDirectory=husi")
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
