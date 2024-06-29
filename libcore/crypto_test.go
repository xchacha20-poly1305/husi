package libcore

import (
	"testing"
)

func TestWireGuardKeyPair(t *testing.T) {
	t.Parallel()
	keyPair := NewWireGuardKeyPair()
	t.Logf("Private key: %s\nPublic Key: %s\n", keyPair.GetPrivateKey(), keyPair.GetPublicKey())
}

func TestSha256Hex(t *testing.T) {
	t.Parallel()
	shaString := Sha256Hex(make([]byte, 32))
	t.Logf("sha256 hex: %s\n", shaString)
}
