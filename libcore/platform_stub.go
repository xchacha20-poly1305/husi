package libcore

import (
	"context"
	"net/netip"
	"os"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/process"
	"github.com/sagernet/sing-box/experimental/libbox/platform"
	"github.com/sagernet/sing-box/option"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"
)

var _ platform.Interface = platformInterfaceStub{}

type platformInterfaceStub struct{}

func (p platformInterfaceStub) Initialize(_ context.Context, _ adapter.Router) error {
	return nil
}

func (p platformInterfaceStub) UsePlatformAutoDetectInterfaceControl() bool {
	return true
}

func (p platformInterfaceStub) AutoDetectInterfaceControl() control.Func {
	return nil
}

func (p platformInterfaceStub) OpenTun(_ *tun.Options, _ option.TunPlatformOptions) (tun.Tun, error) {
	return nil, os.ErrInvalid
}

func (p platformInterfaceStub) UsePlatformDefaultInterfaceMonitor() bool {
	return true
}

func (p platformInterfaceStub) CreateDefaultInterfaceMonitor(_ logger.Logger) tun.DefaultInterfaceMonitor {
	return interfaceMonitorStub{}
}

func (p platformInterfaceStub) UsePlatformInterfaceGetter() bool {
	return true
}

func (p platformInterfaceStub) Interfaces() ([]control.Interface, error) {
	return nil, os.ErrInvalid
}

func (p platformInterfaceStub) UnderNetworkExtension() bool {
	return false
}

func (p platformInterfaceStub) IncludeAllNetworks() bool {
	return false
}

func (p platformInterfaceStub) ClearDNSCache() {
}

func (p platformInterfaceStub) ReadWIFIState() adapter.WIFIState {
	return adapter.WIFIState{}
}

func (p platformInterfaceStub) FindProcessInfo(_ context.Context, _ string, _ netip.AddrPort, _ netip.AddrPort) (*process.Info, error) {
	return &process.Info{}, nil
}

var _ tun.DefaultInterfaceMonitor = interfaceMonitorStub{}

type interfaceMonitorStub struct{}

func (i interfaceMonitorStub) Start() error {
	return nil
}

func (i interfaceMonitorStub) Close() error {
	return nil
}

func (i interfaceMonitorStub) DefaultInterfaceName(_ netip.Addr) string {
	return ""
}

func (i interfaceMonitorStub) DefaultInterfaceIndex(_ netip.Addr) int {
	return -1
}

func (i interfaceMonitorStub) DefaultInterface(_ netip.Addr) (string, int) {
	return "", -1
}

func (i interfaceMonitorStub) OverrideAndroidVPN() bool {
	return false
}

func (i interfaceMonitorStub) AndroidVPNEnabled() bool {
	return false
}

func (i interfaceMonitorStub) RegisterCallback(_ tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	return nil
}

func (i interfaceMonitorStub) UnregisterCallback(_ *list.Element[tun.DefaultInterfaceUpdateCallback]) {
}
