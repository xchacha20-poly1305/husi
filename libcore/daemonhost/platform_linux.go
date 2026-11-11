//go:build linux && !android

package daemonhost

import (
	"context"
	"errors"
	"net/netip"
	"slices"
	"sync"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-tun"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/nmvpn"
	"github.com/xchacha20-poly1305/husi/libcore/v2/platformstubs"
)

const supportsNetworkManagerTun = true

func newNetworkManagerPlatform(ctx context.Context, onPlatformTunLost func()) (adapter.PlatformInterface, error) {
	return &platformInterface{connection: nmvpn.NewDebugTun(ctx, onPlatformTunLost)}, nil
}

var _ adapter.PlatformInterface = (*platformInterface)(nil)

type platformInterface struct {
	platformstubs.PlatformInterface
	connection *nmvpn.DbusTun
	access     sync.RWMutex
	addresses  []netip.Addr
}

func (p *platformInterface) UsePlatformInterface() bool {
	return true
}

func (p *platformInterface) OpenInterface(options *tun.Options, platformOptions option.TunPlatformOptions) (tun.Tun, error) {
	if platformOptions.HTTPProxy != nil && platformOptions.HTTPProxy.Enabled {
		return nil, E.Cause(errors.ErrUnsupported, "platform.http_proxy is unsupported by the NetworkManager backend; configure the desktop proxy separately")
	}
	options.InterfaceMonitor.RegisterMyInterface(options.Name)
	device, err := p.connection.OpenTun(options)
	if err != nil {
		return nil, err
	}
	p.access.Lock()
	p.addresses = make([]netip.Addr, 0, len(options.Inet4Address)+len(options.Inet6Address))
	for _, prefix := range options.Inet4Address {
		p.addresses = append(p.addresses, prefix.Addr())
	}
	for _, prefix := range options.Inet6Address {
		p.addresses = append(p.addresses, prefix.Addr())
	}
	p.access.Unlock()
	return device, nil
}

func (p *platformInterface) MyInterfaceAddress() []netip.Addr {
	p.access.RLock()
	defer p.access.RUnlock()
	return slices.Clone(p.addresses)
}

func (p *platformInterface) Close() error {
	err := p.connection.Close()
	p.access.Lock()
	p.addresses = nil
	p.access.Unlock()
	return err
}
