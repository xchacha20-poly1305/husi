package libcore

import (
	"context"
	"crypto/x509"
	"encoding/pem"
	"os"
	"path/filepath"
	"strings"
	_ "unsafe" // for go:linkname

	_ "github.com/sagernet/sing-box/common/certificate"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"

	scribe "github.com/xchacha20-poly1305/TLS-scribe"
)

//go:linkname systemRoots crypto/x509.systemRoots
var systemRoots *x509.CertPool

//go:linkname mozillaRoots github.com/sagernet/sing-box/common/certificate.mozillaIncluded
var mozillaRoots *x509.CertPool

const customCaFile = "ca.pem"

// UpdateRootCACerts updates Go trusted certs.
// Set certFromJava to get cert with user trusted. ( workaround for: https://github.com/golang/go/issues/71258 )
//
// In each time, this appends externalAssetsPath/ca.pem to root CA.
func UpdateRootCACerts(enableCazilla bool, certFromJava StringIterator) {
	// https://github.com/golang/go/blob/30b6fd60a63c738c2736e83b6a6886a032e6f269/src/crypto/x509/root.go#L31
	// Make sure initialize system cert pool.
	// If system cert has not been initialized,
	// other place, where using x509.SystemCertPool(), will initialize systemRoots and override out hook.
	systemRoots = nil // Clean up old, then x508.SystemCertPool can read again, getting real system certs.
	sysRoots, _ := x509.SystemCertPool()

	var roots *x509.CertPool
	if enableCazilla {
		roots = mozillaRoots.Clone()
	} else {
		if certFromJava == nil {
			roots = sysRoots
		} else {
			roots = x509.NewCertPool()
			for certFromJava.HasNext() {
				cert := certFromJava.Next()
				// Unsupported: CatCert(SHA1WithRSA) since Go 1.24
				if !tryAddCert(roots, []byte(cert)) {
					log.Warn("failed to load java cert: ", cert)
				}
			}
		}
	}

	externalPem, _ := os.ReadFile(filepath.Join(externalAssetsPath, customCaFile))
	if len(externalPem) > 0 {
		if tryAddCert(roots, externalPem) {
			log.Info("loaded external cert")
		} else {
			log.Warn("failed to loaded external cert")
		}
	}

	systemRoots = roots
}

// tryAddCert tries to add raw as pem or DER to pool.
// This not returns error because the error just caused by parsing DER.
func tryAddCert(pool *x509.CertPool, raw []byte) bool {
	if pool.AppendCertsFromPEM(raw) {
		return true
	}
	// Inspired by:
	// https://github.com/MetaCubeX/mihomo/blob/9bfb10d7aefee0799f0116c22479627f312ccf4f/component/ca/config.go#L41-L48
	certs, err := x509.ParseCertificates(raw)
	if err != nil {
		return false
	}
	for _, cert := range certs {
		pool.AddCert(cert)
	}
	return true
}

const (
	ScribeTLS int32 = iota
	ScribeQUIC
)

func GetCert(address, serverName string, mode int32) (cert string, err error) {
	ctx, cancel := context.WithTimeout(context.Background(), C.ProtocolTimeouts[C.ProtocolQUIC])
	defer cancel()

	target := M.ParseSocksaddr(address)
	if target.Port == 0 {
		target.Port = 443
	}
	options := scribe.Option{
		Target: target,
		SNI:    serverName,
	}
	var certs []*x509.Certificate
	switch mode {
	case ScribeTLS:
		certs, err = scribe.GetCert(ctx, options)
	case ScribeQUIC:
		certs, err = scribe.GetCertQuic(ctx, options)
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
