//go:build (linux && !android) || windows

package systemproxy

import (
	"context"

	"github.com/sagernet/sing-box/common/settings"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
)

func Enable(host string, port uint16) error {
	proxy, err := settings.NewSystemProxy(
		context.Background(),
		M.ParseSocksaddrHostPort(host, port),
		true,
		nil,
	)
	if err != nil {
		return E.Cause(err, "create system proxy")
	}
	defer proxy.Close()
	err = proxy.Enable()
	if err != nil {
		return E.Cause(err, "enable system proxy")
	}
	return nil
}

func Disable() error {
	proxy, err := settings.NewSystemProxy(context.Background(), M.Socksaddr{}, true, nil)
	if err != nil {
		return E.Cause(err, "create system proxy")
	}
	defer proxy.Close()
	err = proxy.Disable()
	if err != nil {
		return E.Cause(err, "disable system proxy")
	}
	return nil
}
