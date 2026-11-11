//go:build !linux || android

package nmvpn

import (
	"errors"
	"os"

	"github.com/sagernet/sing-tun"
	E "github.com/sagernet/sing/common/exceptions"
)

func (p *DbusTun) OpenTun(options *tun.Options) (tun.Tun, error) {
	return nil, E.Cause(errors.ErrUnsupported, "NetworkManger tun is only available on Linux")
}

func (p *DbusTun) Close() error {
	return os.ErrInvalid
}
