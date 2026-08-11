//go:build !android

package libcore

// PlatformInterface is empty on desktop after OnGroupSelectedChange / OnTask
// moved off the FFI. Kept as a named type so registerPlatformInterface and
// baseContext keep a stable signature; callers pass nil.
type PlatformInterface interface{}
