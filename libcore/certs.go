package libcore

import (
	"crypto/x509"
	"log"
	_ "unsafe" // for go:linkname

	scribe "github.com/xchacha20-poly1305/TLS-scribe"
	"github.com/xchacha20-poly1305/cazilla"
)

//go:linkname systemRoots crypto/x509.systemRoots
var systemRoots *x509.CertPool

func updateRootCACerts(pem []byte, enabledCazilla bool) {
	roots := func(useMozilla bool) *x509.CertPool {
		if useMozilla {
			log.Println("Using cazilla.")
			return cazilla.CA
		}

		p, _ := x509.SystemCertPool()
		return p
	}(enabledCazilla)

	if len(pem) > 0 {
		if roots.AppendCertsFromPEM(pem) {
			log.Println("external ca.pem was loaded")
		} else {
			log.Println("failed to append certificates from pem")
		}
	}
	systemRoots = roots
}

//go:linkname initSystemRoots crypto/x509.initSystemRoots
func initSystemRoots()

func PinCert(target, serverName string) (cert string, err error) {
	return scribe.Execute(target, serverName)
}
