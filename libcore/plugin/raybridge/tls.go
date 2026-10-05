package raybridge

import (
	"crypto/sha256"
)

// CertChainHash generates V2Ray style cert chain hash.
//
// https://github.com/v2fly/v2ray-core/blob/45e741bae00e2fda57dc8fb911c0ee16fe2e030b/transport/internet/tls/pin.go#L9-L36
func CertChainHash(rawCerts [][]byte) (hash []byte) {
	for _, cert := range rawCerts {
		certHash := sha256.Sum256(cert)
		if hash == nil {
			hash = certHash[:]
			continue
		}
		chainedHash := sha256.Sum256(append(hash, certHash[:]...))
		hash = chainedHash[:]
	}
	return
}
