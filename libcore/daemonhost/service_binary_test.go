package daemonhost

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestInstallCoreStopsBeforeReplacing(t *testing.T) {
	sourceDir := t.TempDir()
	destinationDir := t.TempDir()
	source := filepath.Join(sourceDir, "husi-core")
	destination := filepath.Join(destinationDir, "bin", "husi-core")
	require.NoError(t, os.WriteFile(source, []byte("new"), 0o755))
	require.NoError(t, os.MkdirAll(filepath.Dir(destination), 0o755))
	require.NoError(t, os.WriteFile(destination, []byte("old"), 0o755))

	stopped := false
	err := installCore(source, destination, func() error {
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

func TestInstallCoreMissingSource(t *testing.T) {
	destination := filepath.Join(t.TempDir(), "husi-core")
	err := installCore(filepath.Join(t.TempDir(), "husi-core"), destination, nil)
	require.Error(t, err)
	_, err = os.Stat(destination)
	assert.True(t, os.IsNotExist(err))
}

func TestRemoveCore(t *testing.T) {
	path := filepath.Join(t.TempDir(), "husi-core")
	require.NoError(t, os.WriteFile(path, []byte("binary"), 0o755))

	require.NoError(t, removeCore(path))
	_, err := os.Stat(path)
	assert.True(t, os.IsNotExist(err))
	// Removing again is not an error.
	require.NoError(t, removeCore(path))
}

func useCoreLibraries(t *testing.T, libraries ...string) {
	original := coreLibraries
	coreLibraries = libraries
	t.Cleanup(func() {
		coreLibraries = original
	})
}

func TestInstallCoreCopiesLibraries(t *testing.T) {
	useCoreLibraries(t, "libcronet.so")
	sourceDir := t.TempDir()
	source := filepath.Join(sourceDir, "husi-core")
	destination := filepath.Join(t.TempDir(), "bin", "husi-core")
	require.NoError(t, os.WriteFile(source, []byte("binary"), 0o755))
	require.NoError(t, os.WriteFile(filepath.Join(sourceDir, "libcronet.so"), []byte("library"), 0o644))

	require.NoError(t, installCore(source, destination, nil))

	content, err := os.ReadFile(filepath.Join(filepath.Dir(destination), "libcronet.so"))
	require.NoError(t, err)
	assert.Equal(t, []byte("library"), content)
}

func TestInstallCoreMissingLibraryLeavesNoBinary(t *testing.T) {
	useCoreLibraries(t, "libcronet.so")
	source := filepath.Join(t.TempDir(), "husi-core")
	destination := filepath.Join(t.TempDir(), "husi-core")
	require.NoError(t, os.WriteFile(source, []byte("binary"), 0o755))

	require.Error(t, installCore(source, destination, nil))
	_, err := os.Stat(destination)
	assert.True(t, os.IsNotExist(err))
}

func TestRemoveCoreRemovesLibraries(t *testing.T) {
	useCoreLibraries(t, "libcronet.so")
	directory := t.TempDir()
	path := filepath.Join(directory, "husi-core")
	library := filepath.Join(directory, "libcronet.so")
	require.NoError(t, os.WriteFile(path, []byte("binary"), 0o755))
	require.NoError(t, os.WriteFile(library, []byte("library"), 0o644))

	require.NoError(t, removeCore(path))
	_, err := os.Stat(library)
	assert.True(t, os.IsNotExist(err))
}
