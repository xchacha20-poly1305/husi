package libcore

import (
	"context"
	"net"
	"net/netip"
	"os"

	"libcore/protect"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/process"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/libbox/platform"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"
)

var _ platform.Interface = platformInterfaceStub{}

type platformInterfaceStub struct{}

func (p platformInterfaceStub) Initialize(networkManager adapter.NetworkManager) error {
	return networkManager.UpdateInterfaces()
}

func (p platformInterfaceStub) UsePlatformAutoDetectInterfaceControl() bool {
	return true
}

// AutoDetectInterfaceControl try to protect fd via ProtectPath.
func (p platformInterfaceStub) AutoDetectInterfaceControl(fd int) error {
	_ = protect.Protect(ProtectPath, fd)
	return nil
}

func (p platformInterfaceStub) OpenTun(_ *tun.Options, _ option.TunPlatformOptions) (tun.Tun, error) {
	return nil, os.ErrInvalid
}

func (p platformInterfaceStub) CreateDefaultInterfaceMonitor(_ logger.Logger) tun.DefaultInterfaceMonitor {
	return interfaceMonitorStub{}
}

func (p platformInterfaceStub) UsePlatformInterfaceGetter() bool {
	return true
}

func (p platformInterfaceStub) Interfaces() ([]adapter.NetworkInterface, error) {
	return []adapter.NetworkInterface{
		{
			Interface:   *fakeInterface(),
			Type:        C.InterfaceTypeOther,
			DNSServers:  nil,
			Expensive:   false,
			Constrained: false,
		},
	}, nil
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

func (p platformInterfaceStub) SendNotification(_ *platform.Notification) error {
	return nil
}

var _ tun.DefaultInterfaceMonitor = interfaceMonitorStub{}

type interfaceMonitorStub struct{}

func (i interfaceMonitorStub) Start() error {
	return nil
}

func (i interfaceMonitorStub) Close() error {
	return nil
}

func (i interfaceMonitorStub) DefaultInterface() *control.Interface {
	return nil
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

func fakeInterface() *control.Interface {
	return &control.Interface{
		Index:        0,
		MTU:          1420,
		Name:         "fake",
		HardwareAddr: nil,
		Flags:        net.FlagUp | net.FlagRunning,
	}
}
