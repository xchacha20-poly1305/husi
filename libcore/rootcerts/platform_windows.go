//go:build windows

package rootcerts

import (
	"errors"
	"unsafe"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/windows"
)

// Windows has no default SSL_CERT_{FILE,DIR} paths.
var certFiles, certDirectories []string

func appendPlatformRootCAs(roots *Bundle, withUserTrust bool) error {
	flags := uint32(windows.CERT_SYSTEM_STORE_LOCAL_MACHINE)
	if withUserTrust {
		flags = windows.CERT_SYSTEM_STORE_CURRENT_USER
	}
	err := readWindowsStore("Disallowed", windows.CERT_SYSTEM_STORE_CURRENT_USER, roots.distrust)
	if err != nil {
		log.Warn(E.Cause(err, "read disallowed store"))
	}
	err = readWindowsStore("ROOT", flags, roots.Append)
	if err != nil {
		return err
	}
	if roots.pem.Len() == 0 {
		return E.New("no certificate found")
	}
	return nil
}

func readWindowsStore(name string, flags uint32, consume func([]byte) error) error {
	rootStore, err := windows.UTF16PtrFromString(name)
	if err != nil {
		return err
	}
	store, err := windows.CertOpenStore(
		windows.CERT_STORE_PROV_SYSTEM,
		0,
		0,
		flags|windows.CERT_STORE_READONLY_FLAG,
		uintptr(unsafe.Pointer(rootStore)),
	)
	if err != nil {
		return E.Cause(err, "open Windows certificate store ", name)
	}
	defer windows.CertCloseStore(store, 0)

	for context := (*windows.CertContext)(nil); ; {
		context, err = windows.CertEnumCertificatesInStore(store, context)
		if err != nil {
			if !errors.Is(err, windows.Errno(windows.CRYPT_E_NOT_FOUND)) {
				return E.Cause(err, "enumerate Windows certificate store ", name)
			}
			break
		}
		if context == nil {
			break
		}
		err = consume(unsafe.Slice(context.EncodedCert, context.Length))
		if err != nil {
			log.Warn(E.Cause(err, "read certificate from Windows store ", name))
		}
	}
	return nil
}
