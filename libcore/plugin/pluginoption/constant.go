// Package pluginoption provides options for hooked protocols.
package pluginoption

import (
	C "github.com/sagernet/sing-box/constant"
)

const (
	TypeJuicity = "juicity"
)

func ProxyDisplayName(proxyType string) string {
	switch proxyType {
	case TypeJuicity:
		return "Juicity"
	default:
		return C.ProxyDisplayName(proxyType)
	}
}
