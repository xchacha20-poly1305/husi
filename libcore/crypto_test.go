package libcore

import (
	"testing"
)

func Test_Sha256Hex(t *testing.T) {
	t.Parallel()
	shaString := Sha256Hex([]byte("Do you hear the people sing?"))
	if shaString != "4c5e7f5a461f4e58c23e4898e064cb5fb8d7a414fc678f65cec4f5f17a433f7e" {
		t.Error("failed to test sha256:", shaString)
	}
}
