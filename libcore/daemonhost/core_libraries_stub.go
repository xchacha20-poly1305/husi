//go:build !(with_naive_outbound && with_purego)

package daemonhost

// coreLibraries is empty when husi-core links everything it loads.
var coreLibraries []string
