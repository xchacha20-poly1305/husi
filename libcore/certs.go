package libcore

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/x509"
	"encoding/hex"
	"encoding/pem"
	"net"
	"os"
	"path/filepath"
	_ "unsafe" // for go:linkname

	_ "github.com/sagernet/sing-box/common/certificate"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common/buf"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"

	"libcore/plugin/raybridge"

	scribe "github.com/xchacha20-poly1305/TLS-scribe"
	"github.com/xchacha20-poly1305/cazilla"
)

func init() {
	// Smaller than do nothing and override with nil.
	boxMozillaCert = x509.NewCertPool()
}

//go:linkname systemRoots crypto/x509.systemRoots
//go:linkname boxMozillaCert github.com/sagernet/sing-box/common/certificate.mozillaIncluded
var (
	systemRoots    *x509.CertPool
	boxMozillaCert *x509.CertPool
)

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
		roots = cazilla.CA.Clone()
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

// GetCert try to get cert from create a connection to address with serverName as SNI.
// mode can choose TLS or QUIC.
// proxy is a socks5 URL for dialer.
func GetCert(address, serverName, mode, format, proxy string) (string, error) {
	target := M.ParseSocksaddr(address)
	if target.Port == 0 {
		target.Port = 443
	}
	if !target.IsValid() {
		return "", E.New("invalid server address: ", address)
	}
	var dialer N.Dialer = new(N.DefaultDialer)
	if proxy != "" {
		var err error
		dialer, err = socks.NewClientFromURL(dialer, proxy)
		if err != nil {
			return "", E.Cause(err, "create proxy dialer")
		}
	}

	options := scribe.Option{
		Target: target,
		SNI:    serverName,
		Dialer: dialer,
	}

	ctx, cancel := context.WithTimeout(context.Background(), C.ProtocolTimeouts[C.ProtocolQUIC])
	defer cancel()

	var (
		certs []*x509.Certificate
		err   error
	)
	switch mode {
	case "https":
		certs, err = scribe.GetCert(ctx, options)
	case "quic":
		if target.IsFqdn() {
			ips, err := net.LookupIP(target.Fqdn)
			if err != nil {
				return "", E.Cause(err, "look up ip for ", target.Fqdn)
			}
			if len(ips) == 0 {
				return "", E.New("not found ip for ", target.Fqdn)
			}
			options.Target.Addr = M.AddrFromIP(ips[0])
			options.SNI = target.Fqdn
			options.Target.Fqdn = ""
		}
		certs, err = scribe.GetCertQuic(ctx, options)
	default:
		err = E.New("unknown mode: ", mode)
	}
	if err != nil {
		return "", err
	}

	const typeCert = "CERTIFICATE"
	certBuffer := func() *bytes.Buffer {
		buffer := bytes.NewBuffer(buf.Get(buf.BufferSize)[:0])
		for _, cert := range certs {
			_ = pem.Encode(buffer, &pem.Block{
				Type:  typeCert,
				Bytes: cert.Raw,
			})
		}
		return buffer
	}
	switch format {
	case "v2ray":
		buffer := certBuffer()
		defer buf.Put(buffer.Bytes())
		base64Hash := raybridge.CalculatePEMCertHash(buffer.Bytes())
		return string(base64Hash), nil
	case "hysteria":
		cert := certs[0]
		buffer := bytes.NewBuffer(buf.Get(buf.BufferSize)[:0])
		defer buf.Put(buffer.Bytes())
		_ = pem.Encode(buffer, &pem.Block{
			Type:  typeCert,
			Bytes: cert.Raw,
		})
		hash := sha256.Sum256(buffer.Bytes())
		hashHex := hex.EncodeToString(hash[:])
		return hashHex, nil
	case "raw":
		fallthrough
	default:
		buffer := certBuffer()
		defer buf.Put(buffer.Bytes())
		return buffer.String(), nil
	}
}
