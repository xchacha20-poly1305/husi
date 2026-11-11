package platformstubs

import (
	"context"
	"net/netip"
	"os"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"
)

var _ adapter.PlatformInterface = PlatformInterface{}

type PlatformInterface struct{}

func (p PlatformInterface) Initialize(_ adapter.NetworkManager) error {
	return nil
}

func (p PlatformInterface) UsePlatformAutoDetectInterfaceControl() bool {
	return true
}

func (p PlatformInterface) AutoDetectInterfaceControl(_ int) error {
	return nil
}

func (p PlatformInterface) UsePlatformInterface() bool {
	return false
}

func (p PlatformInterface) OpenInterface(_ *tun.Options, _ option.TunPlatformOptions) (tun.Tun, error) {
	return nil, os.ErrInvalid
}

func (p PlatformInterface) ProcessPlatformOptions(_ option.TunPlatformOptions) error {
	return nil
}

func (p PlatformInterface) UsePlatformDefaultInterfaceMonitor() bool {
	return true
}

func (p PlatformInterface) CreateDefaultInterfaceMonitor(_ logger.Logger) tun.DefaultInterfaceMonitor {
	return interfaceMonitorStub{}
}

func (p PlatformInterface) UsePlatformNetworkInterfaces() bool {
	return false
}

func (p PlatformInterface) NetworkInterfaces() ([]adapter.NetworkInterface, error) {
	return nil, os.ErrInvalid
}

func (p PlatformInterface) RequestPermissionForWIFIState() error {
	return os.ErrInvalid
}

func (p PlatformInterface) UnderNetworkExtension() bool {
	return false
}

func (p PlatformInterface) NetworkExtensionIncludeAllNetworks() bool {
	return false
}

func (p PlatformInterface) ClearDNSCache() {
}

func (p PlatformInterface) UsePlatformWIFIMonitor() bool {
	return false
}

func (p PlatformInterface) ReadWIFIState(_ context.Context) adapter.WIFIState {
	return adapter.WIFIState{}
}

func (p PlatformInterface) UsePlatformConnectionOwnerFinder() bool {
	return true
}

func (p PlatformInterface) FindConnectionOwner(_ *adapter.FindConnectionOwnerRequest) (*adapter.ConnectionOwner, error) {
	return nil, os.ErrInvalid
}

func (p PlatformInterface) UsePlatformNotification() bool {
	return false
}

func (p PlatformInterface) SendNotification(_ *adapter.Notification) error {
	return nil
}

func (p PlatformInterface) CancelNotification(_ string, _ int32) error {
	return nil
}

func (p PlatformInterface) MyInterfaceAddress() []netip.Addr {
	return nil
}

func (p PlatformInterface) UsePlatformShell() bool {
	return false
}

func (p PlatformInterface) CheckPlatformShell() error {
	return os.ErrInvalid
}

func (p PlatformInterface) OpenShellSession(user *adapter.PlatformUser, command string, env []string, term string, rows int32, cols int32) (adapter.ShellSession, error) {
	return nil, os.ErrInvalid
}

func (p PlatformInterface) LookupUser(username string) (*adapter.PlatformUser, error) {
	return nil, os.ErrInvalid
}

func (p PlatformInterface) LookupSFTPServer() (string, error) {
	return "", os.ErrInvalid
}

func (p PlatformInterface) ReadSystemSSHHostKey() ([]byte, error) {
	return nil, os.ErrInvalid
}

func (p PlatformInterface) TailscaleHostname() string {
	return ""
}

var _ tun.DefaultInterfaceMonitor = interfaceMonitorStub{}

type interfaceMonitorStub struct{}

func (i interfaceMonitorStub) RegisterMyInterface(_ string) {
}

func (i interfaceMonitorStub) MyInterfaces() []string {
	return nil
}

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

func (p PlatformInterface) UsePlatformNeighborResolver() bool {
	return false
}

func (p PlatformInterface) StartNeighborMonitor(_ adapter.NeighborUpdateListener) error {
	return os.ErrInvalid
}

func (p PlatformInterface) CloseNeighborMonitor(_ adapter.NeighborUpdateListener) error {
	return os.ErrInvalid
}

func (p PlatformInterface) UsePlatformBridge() bool {
	return false
}

func (p PlatformInterface) CreateBridge(_ adapter.BridgeOptions) (adapter.BridgeSession, error) {
	return nil, os.ErrInvalid
}

func (p PlatformInterface) UsePlatformAutoRedirect() bool {
	return false
}

func (p PlatformInterface) CreateAutoRedirect(_ adapter.AutoRedirectOptions) (adapter.AutoRedirectSession, error) {
	return nil, os.ErrInvalid
}
