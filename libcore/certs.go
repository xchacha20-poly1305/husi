package libcore

import (
	"context"
	"crypto/x509"
	"encoding/pem"
	"os"
	"path/filepath"
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

// UpdateRootCACerts appends externalAssetsPath/ca.pem to root CA.
// By the way, if enabledCazilla == true, it will use the CA trusted by mozilla.
func UpdateRootCACerts(enabledCazilla bool) {
	// https://github.com/golang/go/blob/30b6fd60a63c738c2736e83b6a6886a032e6f269/src/crypto/x509/root.go#L31
	// Make sure initialize system cert pool.
	// If system cert has not been initilize,
	// other place, where using x509.SystemCertPool(),will reload systemRoots.
	_, _ = x509.SystemCertPool()

	var roots *x509.CertPool
	if enabledCazilla {
		roots = x509.NewCertPool()
		_ = roots.AppendCertsFromPEM(cazilla.MozillaIncludedCAPEM) // Must
	} else {
		roots, _ = x509.SystemCertPool()
	}

	externalPem, _ := os.ReadFile(filepath.Join(externalAssetsPath, "ca.pem"))
	if len(externalPem) > 0 {
		if roots.AppendCertsFromPEM(externalPem) {
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
