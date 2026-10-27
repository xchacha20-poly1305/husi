//go:build darwin && cgo

package rootcerts

/*
#cgo LDFLAGS: -framework CoreFoundation -framework Security
#include "platform_darwin.h"
*/
import "C"

import (
	"unsafe"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
)

// macOS has no default SSL_CERT_{FILE,DIR} paths.
var certFiles, certDirectories []string

func appendPlatformRootCAs(roots *Bundle, withUserTrust bool) error {
	var certificates, distrusted C.CFDataRef
	status := C.husi_copy_root_certificates(C.bool(withUserTrust), &certificates, &distrusted)
	if status != C.errSecSuccess {
		return E.New("read macOS root certificates: OSStatus ", status)
	}
	defer C.CFRelease(C.CFTypeRef(certificates))
	defer C.CFRelease(C.CFTypeRef(distrusted))

	err := roots.distrust(darwinCertificateBytes(distrusted))
	if err != nil {
		log.Warn(E.Cause(err, "read macOS distrusted certificates"))
	}
	if err := roots.Append(darwinCertificateBytes(certificates)); err != nil {
		log.Warn(E.Cause(err, "load macOS root certificates"))
	}
	if roots.pem.Len() == 0 {
		return E.New("no certificate found")
	}
	return nil
}

func darwinCertificateBytes(data C.CFDataRef) []byte {
	return unsafe.Slice((*byte)(unsafe.Pointer(C.CFDataGetBytePtr(data))), int(C.CFDataGetLength(data)))
}
