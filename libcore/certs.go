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

func updateRootCACerts(pem []byte) {
	x509.SystemCertPool()
	roots := x509.NewCertPool()
	if !roots.AppendCertsFromPEM(pem) {
		log.Println("failed to append certificates from pem")
		return
	}
	systemRoots = roots
	log.Println("external ca.pem was loaded")
}

func updateCazilla() {
	systemRoots = cazilla.CA
	log.Println("Use cazilla as your CA")
}

//go:linkname initSystemRoots crypto/x509.initSystemRoots
func initSystemRoots()

func PinCert(target, serverName string) (cert string, err error) {
	return scribe.Execute(target, serverName)
}
