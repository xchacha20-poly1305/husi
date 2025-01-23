package libcore

import (
	"crypto/sha256"
	"encoding/hex"
)

// Sha256Hex culculates sha256 sum of data.
func Sha256Hex(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}
