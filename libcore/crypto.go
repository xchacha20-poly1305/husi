package libcore

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"

	"golang.org/x/crypto/curve25519"
)

// WireGuardKeyPair saves WireGuard key pair.
type WireGuardKeyPair struct {
	privateKey, publicKey string
}

const wgKeyLen = 32

// NewWireGuardKeyPair generate key pair from system random bytes.
func NewWireGuardKeyPair() *WireGuardKeyPair {
	// from: golang.zx2c4.com/wireguard/wgctrl/wgtypes

	randomBytes := make([]byte, wgKeyLen)
	_, _ = rand.Read(randomBytes)

	// Modify random bytes using algorithm described at:
	// https://cr.yp.to/ecdh.html.
	randomBytes[0] &= 248
	randomBytes[31] &= 127
	randomBytes[31] |= 64

	keyPair := &WireGuardKeyPair{
		privateKey: base64.StdEncoding.EncodeToString(randomBytes),
	}

	var (
		pub [wgKeyLen]byte
		pri = [wgKeyLen]byte(randomBytes)
	)
	curve25519.ScalarBaseMult(&pub, &pri)
	keyPair.publicKey = base64.StdEncoding.EncodeToString(pub[:])

	return keyPair
}

// GetPrivateKey returns private key of key pair.
func (w *WireGuardKeyPair) GetPrivateKey() string {
	return w.privateKey
}

// GetPublicKey returns publick key of key pair.
func (w *WireGuardKeyPair) GetPublicKey() string {
	return w.publicKey
}

// Sha256Hex culculates sha256 sum of data.
func Sha256Hex(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}
