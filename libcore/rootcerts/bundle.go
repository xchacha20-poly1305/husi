// Package rootcerts loads the TLS root certificates a core process trusts and
// installs them as Go's system roots.
package rootcerts

import (
	"bytes"
	"crypto/sha256"
	"crypto/x509"
	"encoding/asn1"
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
	pool         *x509.CertPool
	pem          bytes.Buffer
	fingerprints fingerprintSet
	distrusted   fingerprintSet
}

func NewBundle() *Bundle {
	return &Bundle{
		pool:         x509.NewCertPool(),
		fingerprints: make(fingerprintSet),
	}
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
//
// A certificate that does not parse is skipped rather than failing the rest:
// system stores may hold certificates Go rejects, such as ones with a negative
// serial number. The returned error lists what was skipped; every certificate
// that parsed is added regardless.
//
// Certificates in [distrustedRoots] are left out silently, whichever store
// still lists them. Platform loaders additionally exclude locally distrusted
// certificates before appending roots.
func (b *Bundle) Append(raw []byte) error {
	certificates, err := splitCertificates(raw)
	var errors []error
	if err != nil {
		errors = append(errors, err)
	}
	for i, der := range certificates {
		fingerprint := sha256.Sum256(der)
		// Stores often list the same root more than once
		// (Linux bundle file and its hashed directory).
		if _, loaded := b.fingerprints[fingerprint]; loaded {
			continue
		}
		if _, isDistrusted := distrustedRoots[fingerprint]; isDistrusted {
			continue
		}
		if _, isDistrusted := b.distrusted[fingerprint]; isDistrusted {
			continue
		}
		certificate, err := x509.ParseCertificate(der)
		if err != nil {
			errors = append(errors, E.Cause(err, "parse certificate #", i))
			continue
		}
		b.fingerprints[fingerprint] = struct{}{}
		b.pool.AddCert(certificate)
		_ = pem.Encode(&b.pem, &pem.Block{Type: typeCert, Bytes: der})
	}
	return E.Errors(errors...)
}

// splitCertificates returns the DER of each certificate in raw without
// parsing it. If raw holds no PEM certificate, it is read as concatenated DER;
// malformed DER ends the split with an error.
func splitCertificates(raw []byte) ([][]byte, error) {
	var certificates [][]byte
	remained := raw
	for {
		block, rest := pem.Decode(remained)
		if block == nil {
			break
		}
		remained = rest
		if block.Type == typeCert {
			certificates = append(certificates, block.Bytes)
		}
	}
	if len(certificates) > 0 {
		return certificates, nil
	}

	remained = raw
	for len(remained) > 0 {
		var element asn1.RawValue
		rest, err := asn1.Unmarshal(remained, &element)
		if err != nil {
			return certificates, E.Cause(err, "split DER certificates")
		}
		certificates = append(certificates, element.FullBytes)
		remained = rest
	}
	return certificates, nil
}

func (b *Bundle) PEM() []byte {
	return b.pem.Bytes()
}

const typeCert = "CERTIFICATE"
