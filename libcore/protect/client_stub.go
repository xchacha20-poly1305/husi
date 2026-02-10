//go:build !unix

package protect

import (
	"os"
)

func Protect(protectPath string, fileDescriptors ...int) error {
	return os.ErrInvalid
}
