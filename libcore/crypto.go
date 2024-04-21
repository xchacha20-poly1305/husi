package libcore

import (
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"strings"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/crypto/curve25519"
)

type WireGuardKeyPair interface {
	GetPrivateKey() (privateKey string)
	GetPublicKey() (publicKey string)
}

var _ WireGuardKeyPair = (*wgKeyPair)(nil)

type wgKeyPair struct {
	privateKey, publicKey string
}

const wgKeyLen = 32

func NewWireGuardKeyPair() (WireGuardKeyPair, error) {
	// from: golang.zx2c4.com/wireguard/wgctrl/wgtypes

	randomBytes := make([]byte, wgKeyLen)
	_, err := rand.Read(randomBytes)
	if err != nil {
		return nil, E.Cause(err, "generate key")
	}

	// Modify random bytes using algorithm described at:
	// https://cr.yp.to/ecdh.html.
	randomBytes[0] &= 248
	randomBytes[31] &= 127
	randomBytes[31] |= 64

	keyPair := &wgKeyPair{
		privateKey: base64.StdEncoding.EncodeToString(randomBytes),
	}

	var (
		pub [wgKeyLen]byte
		pri = [wgKeyLen]byte(randomBytes)
	)
	curve25519.ScalarBaseMult(&pub, &pri)
	keyPair.publicKey = base64.StdEncoding.EncodeToString(pub[:])

	return keyPair, nil
}

func (w *wgKeyPair) GetPrivateKey() (privateKey string) {
	return w.privateKey
}

func (w *wgKeyPair) GetPublicKey() (publicKey string) {
	return w.publicKey
}

func Sha256Hex(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}

// BA:88:45:17:A1
func Sha256OpenSSL(data []byte) string {
	sum := sha256.Sum256(data)
	sumStr := hex.EncodeToString(sum[:])
	sumStr = strings.ToUpper(sumStr)

	sumSlice := strings.Split(sumStr, "")

	var result []string
	for i := 0; i < len(sumSlice)-1; i += 2 {
		appendSlice := strings.Join(sumSlice[i:i+2], "")
		result = append(result, appendSlice)
	}

	return strings.Join(result, ":")
}
