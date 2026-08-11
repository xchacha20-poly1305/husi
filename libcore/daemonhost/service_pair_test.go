package daemonhost

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSiblingCoreLibrary(t *testing.T) {
	shim := filepath.Join("opt", "husi", "husi-core")
	lib := SiblingCoreLibrary(shim)
	assert.Equal(t, filepath.Join("opt", "husi", CoreLibraryFileName()), lib)
}

func TestResolvePairSources(t *testing.T) {
	dir := t.TempDir()
	shim := filepath.Join(dir, "husi-core")
	lib := filepath.Join(dir, CoreLibraryFileName())
	require.NoError(t, os.WriteFile(shim, []byte("shim"), 0o755))
	require.NoError(t, os.WriteFile(lib, []byte("lib"), 0o644))

	gotShim, gotLib, err := resolvePairSources(shim)
	require.NoError(t, err)
	assert.Equal(t, mustAbs(t, shim), gotShim)
	assert.Equal(t, mustAbs(t, lib), gotLib)
}

func TestResolvePairSourcesMissingLibrary(t *testing.T) {
	dir := t.TempDir()
	shim := filepath.Join(dir, "husi-core")
	require.NoError(t, os.WriteFile(shim, []byte("shim"), 0o755))

	_, _, err := resolvePairSources(shim)
	require.Error(t, err)
	assert.Contains(t, err.Error(), "core library not found")
}

func TestInstallPairLibraryFirstThenShim(t *testing.T) {
	srcDir := t.TempDir()
	dstDir := t.TempDir()
	srcShim := filepath.Join(srcDir, "husi-core")
	srcLib := filepath.Join(srcDir, CoreLibraryFileName())
	require.NoError(t, os.WriteFile(srcShim, []byte("new-shim"), 0o755))
	require.NoError(t, os.WriteFile(srcLib, []byte("new-lib"), 0o644))

	// Pre-seed destination with stale pair so replace is exercised.
	dstShim := filepath.Join(dstDir, "husi-core")
	dstLib := SiblingCoreLibrary(dstShim)
	require.NoError(t, os.WriteFile(dstShim, []byte("old-shim"), 0o755))
	require.NoError(t, os.WriteFile(dstLib, []byte("old-lib"), 0o644))

	stopped := false
	err := installPair(srcShim, srcLib, dstShim, func() error {
		// While stopped, only library should be replaceable mid-flight — we
		// just record that stop ran before either file was updated.
		stopped = true
		lib, err := os.ReadFile(dstLib)
		require.NoError(t, err)
		shim, err := os.ReadFile(dstShim)
		require.NoError(t, err)
		assert.Equal(t, []byte("old-lib"), lib)
		assert.Equal(t, []byte("old-shim"), shim)
		return nil
	})
	require.NoError(t, err)
	assert.True(t, stopped)

	lib, err := os.ReadFile(dstLib)
	require.NoError(t, err)
	shim, err := os.ReadFile(dstShim)
	require.NoError(t, err)
	assert.Equal(t, []byte("new-lib"), lib)
	assert.Equal(t, []byte("new-shim"), shim)
}

func TestInstallPairFailsWhenLibraryMissing(t *testing.T) {
	srcDir := t.TempDir()
	dstDir := t.TempDir()
	srcShim := filepath.Join(srcDir, "husi-core")
	require.NoError(t, os.WriteFile(srcShim, []byte("shim"), 0o755))
	// no library source
	dstShim := filepath.Join(dstDir, "husi-core")
	err := installPair(srcShim, filepath.Join(srcDir, CoreLibraryFileName()), dstShim, nil)
	require.Error(t, err)

	// Destination shim must not have been written.
	_, err = os.Stat(dstShim)
	assert.True(t, os.IsNotExist(err))
}

func TestRemovePair(t *testing.T) {
	dir := t.TempDir()
	shim := filepath.Join(dir, "husi-core")
	lib := SiblingCoreLibrary(shim)
	require.NoError(t, os.WriteFile(shim, []byte("s"), 0o755))
	require.NoError(t, os.WriteFile(lib, []byte("l"), 0o644))

	require.NoError(t, removePair(shim))
	_, err := os.Stat(shim)
	assert.True(t, os.IsNotExist(err))
	_, err = os.Stat(lib)
	assert.True(t, os.IsNotExist(err))

	// Idempotent on missing files.
	require.NoError(t, removePair(shim))
}

func TestEnsurePairPresent(t *testing.T) {
	dir := t.TempDir()
	shim := filepath.Join(dir, "husi-core")
	require.NoError(t, os.WriteFile(shim, []byte("s"), 0o755))
	require.Error(t, ensurePairPresent(shim))

	require.NoError(t, os.WriteFile(SiblingCoreLibrary(shim), []byte("l"), 0o644))
	require.NoError(t, ensurePairPresent(shim))
}

func mustAbs(t *testing.T, path string) string {
	t.Helper()
	abs, err := filepath.Abs(path)
	require.NoError(t, err)
	// Match resolveExecutablePath (EvalSymlinks + Abs).
	resolved, err := filepath.EvalSymlinks(abs)
	require.NoError(t, err)
	abs, err = filepath.Abs(resolved)
	require.NoError(t, err)
	return abs
}
