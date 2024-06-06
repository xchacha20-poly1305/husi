package libcore

import (
	"context"
	"crypto/x509"
	"encoding/pem"
	"strings"
	_ "unsafe" // for go:linkname

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	scribe "github.com/xchacha20-poly1305/TLS-scribe"
	"github.com/xchacha20-poly1305/cazilla"
)

//go:linkname systemRoots crypto/x509.systemRoots
var systemRoots *x509.CertPool

func updateRootCACerts(pem []byte, enabledCazilla bool) {
	roots := func(useMozilla bool) *x509.CertPool {
		if useMozilla {
			log.Info("Using cazilla.")
			return cazilla.CA
		}

		p, _ := x509.SystemCertPool()
		return p
	}(enabledCazilla)

	if len(pem) > 0 {
		if roots.AppendCertsFromPEM(pem) {
			log.Info("external ca.pem was loaded")
		} else {
			log.Warn("failed to append certificates from pem")
		}
	}
	systemRoots = roots
}

const (
	ScribeTLS int32 = iota
	ScribeQUIC
)

func GetCert(address, serverName string, mode int32) (cert string, err error) {
	ctx, cancel := context.WithTimeout(context.Background(), C.QUICTimeout)
	defer cancel()

	var certs []*x509.Certificate
	switch mode {
	case ScribeTLS:
		certs, err = scribe.GetCert(ctx, address, serverName)
	case ScribeQUIC:
		certs, err = scribe.GetCertQuic(ctx, address, serverName)
	default:
		err = E.New("unknown mode: ", mode)
	}
	if err != nil {
		return "", err
	}

	var builder strings.Builder
	for _, cert := range certs {
		common.Must(pem.Encode(&builder, &pem.Block{
			Type:  "CERTIFICATE",
			Bytes: cert.Raw,
		}))
	}

	return builder.String(), nil
}
