// Package rootcerts loads the TLS root certificates a core process trusts and
// installs them as Go's system roots.
package rootcerts

import (
	"bytes"
	"crypto/x509"
	"encoding/pem"
	_ "unsafe" // for go:linkname

	_ "github.com/sagernet/sing-box/common/certificate"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	husiv1 "github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
)

//go:linkname systemRoots crypto/x509.systemRoots
var systemRoots *x509.CertPool

//go:linkname chromeIncludedPEM github.com/sagernet/sing-box/common/certificate.chromeIncludedPEM
func chromeIncludedPEM() string

//go:linkname mozillaIncludedPEM github.com/sagernet/sing-box/common/certificate.mozillaIncludedPEM
func mozillaIncludedPEM() string

// Store selects where the roots come from.
type Store int32

const (
	StoreSystem Store = iota
	StoreSystemWithUserTrust
	StoreMozilla
	StoreChrome
)

// StoreFromProto maps the wire enum. It reports false for UNSPECIFIED, which
// asks the host to keep its current roots.
func StoreFromProto(store husiv1.RootCertificateStore) (Store, bool) {
	switch store {
	case husiv1.RootCertificateStore_ROOT_CERTIFICATE_STORE_SYSTEM:
		return StoreSystem, true
	case husiv1.RootCertificateStore_ROOT_CERTIFICATE_STORE_SYSTEM_AND_USER:
		return StoreSystemWithUserTrust, true
	case husiv1.RootCertificateStore_ROOT_CERTIFICATE_STORE_MOZILLA:
		return StoreMozilla, true
	case husiv1.RootCertificateStore_ROOT_CERTIFICATE_STORE_CHROME:
		return StoreChrome, true
	default:
		return 0, false
	}
}

// Bundle is a root set kept both as a pool for Go and as PEM for processes
// that read a certificate file.
type Bundle struct {
	pool *x509.CertPool
	pem  bytes.Buffer
}

func NewBundle() *Bundle {
	return &Bundle{pool: x509.NewCertPool()}
}

// Load reads the roots of store. A store that cannot be read falls back to
// Mozilla's bundle, so the result is never empty.
//
// With [StoreSystem] and [StoreSystemWithUserTrust], the SSL_CERT_FILE and
// SSL_CERT_DIR environment variables take precedence over the platform store.
func Load(store Store) *Bundle {
	roots := NewBundle()
	var err error
	switch store {
	case StoreSystem:
		err = appendSystemRootCAs(roots, false)
	case StoreSystemWithUserTrust:
		err = appendSystemRootCAs(roots, true)
	case StoreMozilla:
		err = roots.Append([]byte(mozillaIncludedPEM()))
	case StoreChrome:
		err = roots.Append([]byte(chromeIncludedPEM()))
	default:
		err = E.New("unknown root certificate store: ", store)
	}
	if err == nil {
		return roots
	}
	log.Error("load root certificates: ", err)
	roots = NewBundle()
	// The embedded bundle is known to parse.
	_ = roots.Append([]byte(mozillaIncludedPEM()))
	return roots
}

// Install makes bundle the roots every Go TLS client of this process trusts.
func Install(bundle *Bundle) {
	// https://github.com/golang/go/blob/30b6fd60a63c738c2736e83b6a6886a032e6f269/src/crypto/x509/root.go#L31
	// x509 fills systemRoots once, on first use. Load the real system roots now,
	// or a later x509.SystemCertPool() call would overwrite ours.
	systemRoots = nil
	_, _ = x509.SystemCertPool()
	systemRoots = bundle.pool
}

// Append adds the certificates of raw, which is PEM or concatenated DER.
func (b *Bundle) Append(raw []byte) error {
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
		err = b.add(certificate)
		if err != nil {
			return err
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
		err = b.add(certificate)
		if err != nil {
			return err
		}
	}
	return nil
}

func (b *Bundle) add(certificate *x509.Certificate) error {
	b.pool.AddCert(certificate)
	err := pem.Encode(&b.pem, &pem.Block{Type: typeCert, Bytes: certificate.Raw})
	if err != nil {
		return E.Cause(err, "encode certificate")
	}
	return nil
}

func (b *Bundle) PEM() []byte {
	return b.pem.Bytes()
}

const typeCert = "CERTIFICATE"
