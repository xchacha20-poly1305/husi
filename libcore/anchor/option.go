package anchor

import (
	"github.com/sagernet/sing/common/auth"
)

type Options struct {
	SocksPort  uint16
	User       auth.User
	DnsPort    uint16
	DeviceName string
}
