package libcore

import (
	"testing"
)

func TestWireGuardKeyPair(t *testing.T) {
	t.Parallel()
	keyPair, err := NewWireGuardKeyPair()
	if err != nil {
		t.Errorf("Failed to generate key pair: %v", err)
		return
	}

	t.Logf("Private key: %s\nPublic Key: %s\n", keyPair.GetPrivateKey(), keyPair.GetPublicKey())
}
