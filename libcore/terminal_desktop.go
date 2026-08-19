//go:build !android

package libcore

import (
	"os"
)

func IsTerminal(fd int32) bool {
	// Windows stdin, stdout, stderr is not 0, 1, 2. So map here.
	var toCheck uintptr
	switch fd {
	case 0:
		toCheck = os.Stdin.Fd()
	case 1:
		toCheck = os.Stdout.Fd()
	case 2:
		toCheck = os.Stderr.Fd()
	default:
		return false
	}
	// isTerminal implementation is copied from: https://github.com/golang/term/blob/9f69229da31ca6a34b522f59dbe07cad5ea21587/term.go#L24-L27
	return isTerminal(int(toCheck))
}
