//go:build windows

package libcore

import (
	"unsafe"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/windows"
)

// Windows has no default SSL_CERT_{FILE,DIR} paths.
var certFiles, certDirectories []string

func appendPlatformRootCAs(roots *rootCABundle, withUserTrust bool) error {
	rootStore, err := windows.UTF16PtrFromString("ROOT")
	if err != nil {
		return err
	}
	var flags uint32
	if withUserTrust {
		flags |= windows.CERT_SYSTEM_STORE_CURRENT_USER
	} else {
		flags |= windows.CERT_SYSTEM_STORE_LOCAL_MACHINE
	}
	store, err := windows.CertOpenStore(
		windows.CERT_STORE_PROV_SYSTEM,
		0,
		0,
		flags,
		uintptr(unsafe.Pointer(rootStore)),
	)
	if err != nil {
		return E.Cause(err, "open Windows root store")
	}
	defer windows.CertCloseStore(store, 0)

	for context := (*windows.CertContext)(nil); ; {
		context, err = windows.CertEnumCertificatesInStore(store, context)
		if err != nil {
			log.Warn(E.Cause(err, "CertEnumCertificatesInStore"))
			break
		}
		if context == nil {
			break
		}
		if err := roots.Append(unsafe.Slice(context.EncodedCert, context.Length)); err != nil {
			return E.Cause(err, "load certificate from Windows root store")
		}
	}
	if roots.pem.Len() == 0 {
		return E.New("no certificate found")
	}
	return nil
}
