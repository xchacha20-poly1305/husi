//go:build with_naive_outbound && with_purego

package daemonhost

import "runtime"

// coreLibraries are the shared libraries husi-core loads from its own directory
// at runtime. A copy of the binary installed elsewhere needs them beside it.
var coreLibraries = []string{cronetLibraryName()}

func cronetLibraryName() string {
	if runtime.GOOS == "windows" {
		return "libcronet.dll"
	}
	return "libcronet.so"
}
