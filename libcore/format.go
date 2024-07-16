package libcore

import (
	"bytes"
	"context"
	"net/netip"
	"os"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/humanize"
	"github.com/sagernet/sing-box/common/process"
	"github.com/sagernet/sing-box/experimental/libbox/platform"
	"github.com/sagernet/sing-box/option"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"
)

// FormatBytes formats the bytes to humanize.
func FormatBytes(length int64) string {
	return humanize.Bytes(uint64(length))
}

// parseConfig parses configContent to option.Options.
func parseConfig(configContent string) (option.Options, error) {
	options, err := json.UnmarshalExtended[option.Options]([]byte(configContent))
	if err != nil {
		return option.Options{}, E.Cause(err, "decode config")
	}
	return options, nil
}

// FormatConfig formats json.
func FormatConfig(configContent string) (string, error) {
	configMap, err := json.UnmarshalExtended[map[string]any]([]byte(configContent))
	if err != nil {
		return "", err
	}

	var buffer bytes.Buffer
	encoder := json.NewEncoder(&buffer)
	encoder.SetIndent("", "  ")
	err = encoder.Encode(configMap)
	if err != nil {
		return "", err
	}

	return buffer.String(), nil
}

// CheckConfig checks configContent wheather can run as sing-box configuration.
func CheckConfig(configContent string) error {
	options, err := parseConfig(configContent)
	if err != nil {
		return E.Cause(err, "parse config")
	}
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	instance, err := box.New(box.Options{
		Options:           options,
		Context:           ctx,
		PlatformInterface: platformInterfaceStub{},
	})
	if err != nil {
		return E.Cause(err, "create box")
	}
	defer instance.Close()
	return nil
}

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
	return nil, os.ErrInvalid
}

var _ tun.DefaultInterfaceMonitor = interfaceMonitorStub{}

type interfaceMonitorStub struct{}

func (i interfaceMonitorStub) Start() error {
	return os.ErrInvalid
}

func (i interfaceMonitorStub) Close() error {
	return os.ErrInvalid
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
