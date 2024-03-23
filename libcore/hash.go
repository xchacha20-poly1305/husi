package libcore

import (
	"crypto/sha256"
	"encoding/hex"
	"strings"
)

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
