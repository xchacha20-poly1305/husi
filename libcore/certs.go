package libcore

import (
	"bytes"
	"context"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/pem"
	"net"
	"os"
	"path/filepath"
	_ "unsafe" // for go:linkname

	_ "github.com/sagernet/sing-box/common/certificate"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

	scribe "github.com/xchacha20-poly1305/TLS-scribe"
	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/raybridge"
	"github.com/xchacha20-poly1305/husi/libcore/v2/simpleproxyurl"
)

//go:linkname systemRoots crypto/x509.systemRoots
var systemRoots *x509.CertPool

//go:linkname chromeIncludedPEM github.com/sagernet/sing-box/common/certificate.chromeIncludedPEM
func chromeIncludedPEM() string

//go:linkname mozillaIncludedPEM github.com/sagernet/sing-box/common/certificate.mozillaIncludedPEM
func mozillaIncludedPEM() string

const (
	CertSystem int32 = iota
	CertWithUserTrust
	CertMozilla
	CertChrome
)

const (
	customCaFile = "ca.pem"
	PluginCaFile = "plugin-ca.pem"
)

// SetupRootCA updates Go trusted certs and creates the PEM bundle for external plugins.
//
// On Android, this appends externalAssetsPath/ca.pem to root CA.
func SetupRootCA(certOption int32) {
	// https://github.com/golang/go/blob/30b6fd60a63c738c2736e83b6a6886a032e6f269/src/crypto/x509/root.go#L31
	// Make sure initialize system cert pool.
	// If system cert has not been initialized,
	// other place, where using x509.SystemCertPool(), will initialize systemRoots and override out hook.
	systemRoots = nil // Clean up old, then x508.SystemCertPool can read again, getting real system certs.
	_, _ = x509.SystemCertPool()

	roots := newRootCABundle()
	var err error
	switch certOption {
	case CertSystem:
		err = appendSystemRootCAs(roots, false)
	case CertWithUserTrust:
		err = appendSystemRootCAs(roots, true)
	case CertMozilla:
		err = roots.Append([]byte(mozillaIncludedPEM()))
	case CertChrome:
		err = roots.Append([]byte(chromeIncludedPEM()))
	default:
		panic("unknown cert option")
	}
	if err != nil {
		log.Error("load root certificates: ", err)
		roots = newRootCABundle()
		fallbackErr := roots.Append([]byte(mozillaIncludedPEM()))
		if fallbackErr != nil {
			log.Error("load fallback Mozilla certificates: ", fallbackErr)
			return
		}
	}

	if C.IsAndroid {
		externalPem, _ := os.ReadFile(filepath.Join(externalAssetsPath, customCaFile))
		if len(externalPem) > 0 {
			err := roots.Append(externalPem)
			if err != nil {
				log.Error(E.Cause(err, "load external cert"))
			} else {
				log.Info("loaded external cert")
			}
		}
	}
	systemRoots = roots.pool

	err = os.MkdirAll(externalAssetsPath, 0o700)
	if err != nil {
		log.Error("create plugin certificate directory: ", err)
		return
	}
	err = os.WriteFile(filepath.Join(externalAssetsPath, PluginCaFile), roots.pem.Bytes(), 0o600)
	if err != nil {
		log.Error("write plugin root certificates: ", err)
		return
	}
}

type rootCABundle struct {
	pool *x509.CertPool
	pem  bytes.Buffer
}

func newRootCABundle() *rootCABundle {
	return &rootCABundle{pool: x509.NewCertPool()}
}

func (b *rootCABundle) Append(raw []byte) error {
	foundPEM := false
	remaining := raw
	for {
		block, rest := pem.Decode(remaining)
		if block == nil {
			break
		}
		remaining = rest
		if block.Type != typeCert {
			continue
		}
		foundPEM = true
		certificate, err := x509.ParseCertificate(block.Bytes)
		if err != nil {
			return E.Cause(err, "parse PEM certificate")
		}
		b.pool.AddCert(certificate)
		err = pem.Encode(&b.pem, &pem.Block{Type: typeCert, Bytes: certificate.Raw})
		if err != nil {
			return E.Cause(err, "encode PEM certificate")
		}
	}
	if foundPEM {
		return nil
	}

	certificates, err := x509.ParseCertificates(raw)
	if err != nil {
		return err
	}
	for _, certificate := range certificates {
		b.pool.AddCert(certificate)
		if err := pem.Encode(&b.pem, &pem.Block{Type: typeCert, Bytes: certificate.Raw}); err != nil {
			return E.Cause(err, "encode DER certificate")
		}
	}
	return nil
}

const typeCert = "CERTIFICATE"

func getCert(ctx context.Context, address, serverName, mode, proxy string) (string, error) {
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
		dialer, err = simpleproxyurl.ProxyFromURL(ctx, proxy)
		if err != nil {
			return "", E.Cause(err, "create proxy dialer")
		}
	}

	options := scribe.Option{
		Target: target,
		SNI:    serverName,
		Dialer: dialer,
	}

	ctx, cancel := context.WithTimeout(ctx, C.ProtocolTimeouts[C.ProtocolQUIC])
	defer cancel()

	var (
		certs []*x509.Certificate
		err   error
	)
	switch mode {
	case "https":
		certs, err = scribe.GetCert(ctx, options)
	case "quic":
		if target.IsDomain() {
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

	buffer := bytes.NewBuffer(nil)
	for _, cert := range certs {
		_ = pem.Encode(buffer, &pem.Block{
			Type:  typeCert,
			Bytes: cert.Raw,
		})
	}
	return buffer.String(), nil
}

func ToV2RayPemHash(rawPem string) string {
	return string(raybridge.CalculatePEMCertHash([]byte(rawPem)))
}

func ToHysteriaHexSha256(rawPem string) string {
	block, _ := pem.Decode([]byte(rawPem))
	if block == nil {
		return ""
	}
	sum256 := sha256.Sum256(block.Bytes)
	hashHex := hex.EncodeToString(sum256[:])
	return hashHex
}

func ToSingPublicKeySha256(rawPem string) (string, error) {
	block, _ := pem.Decode([]byte(rawPem))
	if block == nil {
		return "", E.New("failed to decode pem")
	}
	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		return "", E.Cause(err, "parse certificate")
	}
	publicKey, err := x509.MarshalPKIXPublicKey(cert.PublicKey)
	if err != nil {
		return "", E.Cause(err, "marshal public key")
	}
	sum256 := sha256.Sum256(publicKey)
	return base64.StdEncoding.EncodeToString(sum256[:]), nil
}
