package raybridge

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/pem"
)

// CalculatePEMCertHash generates V2Ray style cert hash.
//
// https://github.com/v2fly/v2ray-core/blob/45e741bae00e2fda57dc8fb911c0ee16fe2e030b/transport/internet/tls/pin.go#L9-L36
func CalculatePEMCertHash(certContents []byte) []byte {
	var certChain [][]byte
	for {
		block, rest := pem.Decode(certContents)
		if block == nil {
			break
		}
		certChain = append(certChain, block.Bytes)
		certContents = rest
	}
	hash := CertChainHash(certChain)

	buffer := make([]byte, base64.StdEncoding.EncodedLen(len(hash)))
	base64.StdEncoding.Encode(buffer, hash)
	return buffer
}

func CertChainHash(rawCerts [][]byte) (hash []byte) {
	for _, cert := range rawCerts {
		sum := sha256.Sum256(cert)
		hash = append(hash, sum[:]...)
	}
	return
}
