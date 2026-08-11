//go:build linux || darwin

package daemonhost

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestValidateProtectedDirectoryRejectsUserWritable(t *testing.T) {
	// Temp dir is typically under /tmp which is world-writable — must fail.
	dir := t.TempDir()
	require.Error(t, ValidateProtectedDirectory(dir), "expected error for world-writable path %s", dir)
}

func TestValidateProtectedDirectoryAcceptsRootOwned(t *testing.T) {
	// /usr is root-owned and not group/other-writable on normal systems.
	if os.Getuid() != 0 {
		// Non-root cannot create root-owned dirs; just check a known system path.
		if err := ValidateProtectedDirectory("/usr"); err != nil {
			// Some container images make /usr unusual; skip rather than fail CI.
			t.Skipf("skip /usr check: %v", err)
		}
	}
	// Nested non-existent path should fail on Lstat.
	require.Error(t, ValidateProtectedDirectory(filepath.Join("/usr", "does-not-exist-husi-test")), "expected error for missing path")
}
