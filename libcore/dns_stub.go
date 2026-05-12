//go:build !android

package libcore

import "github.com/sagernet/sing-box/dns"

func registerPlatformLocalDNSTransport(_ *dns.TransportRegistry, _ PlatformInterface) {
}
