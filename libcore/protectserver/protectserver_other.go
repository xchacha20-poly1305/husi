//go:build !linux

package protectserver

import "io"

func ServeProtect(path string, verbose bool, fwmark int, protectCtl func(fd int)) io.Closer {
	return nil
}
