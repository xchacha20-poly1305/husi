//go:build cgo && with_naive_outbound

package distro

// Workaround: force-link dns_platform_android_attempt.o from libcronet.a.
//
// The pinned cronet-go/lib/android_arm64 archive (Chromium 148) ships
// net::DnsPlatformAndroidAttempt as the platform DNS backend, but the only
// references that survive cronet-go's CGo glue are weak. The static linker
// drops the .o; both JUMP_SLOTs resolve to NULL; the CronetNet thread
// SIGSEGVs (PC=0) when a naive outbound starts. Taking the address of one
// symbol emits a strong reference, which pulls the .o in and transitively
// satisfies the sibling weak ref.
//
// `#cgo LDFLAGS: -Wl,-u,...` would be the canonical form, but Go's cgo
// flag allowlist rejects -Wl,-u (https://go.dev/s/invalidflag). The strong
// reference is therefore emitted from C code instead.

/*
extern void cronet_keep_dns_platform_android_ctor(void)
    __asm__("_ZN3net25DnsPlatformAndroidAttempt12DelegateImplC1Ev");

void (*const cronet_keep_dns_platform_android_ref)(void) =
    cronet_keep_dns_platform_android_ctor;
*/
import "C"
