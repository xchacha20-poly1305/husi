//go:build !android

package libcore

import (
	"github.com/xchacha20-poly1305/husi/libcore/v2/systemproxy"
)

func SetSystemProxy(host string, port int32) error {
	return systemproxy.Enable(host, uint16(port))
}

func ClearSystemProxy() error {
	return systemproxy.Disable()
}
