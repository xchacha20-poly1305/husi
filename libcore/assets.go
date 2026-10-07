package libcore

import (
	"os"
	"path/filepath"
)

var (
	internalAssetsPath string
	externalAssetsPath string
)

// deleteDeprecated deletes the unused file in current version.
// Now will delete:
//
// Clash dashboard,
//
// "geo/pax_global_header", which was added by invalid untargz implementation.
func deleteDeprecated() {
	dashboardPath := filepath.Join(internalAssetsPath, "dashboard")
	_ = os.RemoveAll(dashboardPath)
	_ = os.Remove(dashboardPath + "tgz")
	_ = os.Remove(dashboardPath + versionSuffix)

	_ = os.Remove(filepath.Join(externalAssetsPath, "geo", "pax_global_header"))
}

const versionSuffix = ".version.txt"
