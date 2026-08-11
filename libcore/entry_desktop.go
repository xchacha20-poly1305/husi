//go:build !android

package libcore

// Version is the husi release version stamped at link time via
// -ldflags "-X libcore.Version=…". Defaults to "dev" for unstamped builds.
// Used by the desktop core host entry (coreentry) for session/daemon reporting.
var Version = "dev"
