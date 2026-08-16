package libcore

import (
	"net/netip"
	"os"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"
)

var _ adapter.PlatformInterface = platformInterfaceStub{}

type platformInterfaceStub struct{}

func (p platformInterfaceStub) Initialize(_ adapter.NetworkManager) error {
	return nil
}

func (p platformInterfaceStub) UsePlatformAutoDetectInterfaceControl() bool {
	return true
}

func (p platformInterfaceStub) AutoDetectInterfaceControl(_ int) error {
	return nil
}

func (p platformInterfaceStub) UsePlatformInterface() bool {
	return false
}

func (p platformInterfaceStub) OpenInterface(_ *tun.Options, _ option.TunPlatformOptions) (tun.Tun, error) {
	return nil, os.ErrInvalid
}

func (p platformInterfaceStub) ProcessPlatformOptions(_ option.TunPlatformOptions) error {
	return nil
}

func (p platformInterfaceStub) UsePlatformDefaultInterfaceMonitor() bool {
	return true
}

func (p platformInterfaceStub) CreateDefaultInterfaceMonitor(_ logger.Logger) tun.DefaultInterfaceMonitor {
	return interfaceMonitorStub{}
}

func (p platformInterfaceStub) UsePlatformNetworkInterfaces() bool {
	return false
}

func (p platformInterfaceStub) NetworkInterfaces() ([]adapter.NetworkInterface, error) {
	return nil, os.ErrInvalid
}

func (p platformInterfaceStub) RequestPermissionForWIFIState() error {
	return os.ErrInvalid
}

func (p platformInterfaceStub) UnderNetworkExtension() bool {
	return false
}

func (p platformInterfaceStub) NetworkExtensionIncludeAllNetworks() bool {
	return false
}

func (p platformInterfaceStub) ClearDNSCache() {
}

func (p platformInterfaceStub) UsePlatformWIFIMonitor() bool {
	return false
}

func (p platformInterfaceStub) ReadWIFIState() adapter.WIFIState {
	return adapter.WIFIState{}
}

func (p platformInterfaceStub) UsePlatformConnectionOwnerFinder() bool {
	return true
}

func (p platformInterfaceStub) FindConnectionOwner(_ *adapter.FindConnectionOwnerRequest) (*adapter.ConnectionOwner, error) {
	return nil, os.ErrInvalid
}

func (p platformInterfaceStub) UsePlatformNotification() bool {
	return false
}

func (p platformInterfaceStub) SendNotification(_ *adapter.Notification) error {
	return nil
}

func (p platformInterfaceStub) CancelNotification(_ string, _ int32) error {
	return nil
}

func (p platformInterfaceStub) SystemCertificates() []string {
	return nil
}

func (p platformInterfaceStub) MyInterfaceAddress() []netip.Addr {
	return nil
}

func (p platformInterfaceStub) UsePlatformShell() bool {
	return false
}

func (p platformInterfaceStub) CheckPlatformShell() error {
	return os.ErrInvalid
}

func (p platformInterfaceStub) OpenShellSession(user *adapter.PlatformUser, command string, env []string, term string, rows int32, cols int32) (adapter.ShellSession, error) {
	return nil, os.ErrInvalid
}

func (p platformInterfaceStub) LookupUser(username string) (*adapter.PlatformUser, error) {
	return nil, os.ErrInvalid
}

func (p platformInterfaceStub) LookupSFTPServer() (string, error) {
	return "", os.ErrInvalid
}

func (p platformInterfaceStub) ReadSystemSSHHostKey() ([]byte, error) {
	return nil, os.ErrInvalid
}

func (p platformInterfaceStub) TailscaleHostname() string {
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

func (p platformInterfaceStub) UsePlatformNeighborResolver() bool {
	return false
}

func (p platformInterfaceStub) StartNeighborMonitor(_ adapter.NeighborUpdateListener) error {
	return os.ErrInvalid
}

func (p platformInterfaceStub) CloseNeighborMonitor(_ adapter.NeighborUpdateListener) error {
	return os.ErrInvalid
}

func (p platformInterfaceStub) UsePlatformBridge() bool {
	return false
}

func (p platformInterfaceStub) CreateBridge(_ adapter.BridgeOptions) (adapter.BridgeSession, error) {
	return nil, os.ErrInvalid
}
