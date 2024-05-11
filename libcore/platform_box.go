package libcore

import (
	"context"
	"net/netip"
	"syscall"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/process"
	"github.com/sagernet/sing-box/experimental/libbox/platform"
	"github.com/sagernet/sing-box/option"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/common/logger"
	N "github.com/sagernet/sing/common/network"

	"libcore/procfs"
)

type boxPlatformInterfaceWrapper struct {
	router adapter.Router
}

var _ platform.Interface = (*boxPlatformInterfaceWrapper)(nil)

type WIFIState struct {
	SSID  string
	BSSID string
}

func NewWIFIState(wifiSSID string, wifiBSSID string) *WIFIState {
	return &WIFIState{wifiSSID, wifiBSSID}
}

func (w *boxPlatformInterfaceWrapper) ReadWIFIState() adapter.WIFIState {
	if intfBox != nil {
		wifiState := intfBox.ReadWIFIState()
		if wifiState != nil {
			return (adapter.WIFIState)(*wifiState)
		}
	}
	return adapter.WIFIState{}
}

func (w *boxPlatformInterfaceWrapper) Initialize(ctx context.Context, router adapter.Router) error {
	w.router = router
	return nil
}

func (w *boxPlatformInterfaceWrapper) UsePlatformAutoDetectInterfaceControl() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) AutoDetectInterfaceControl() control.Func {
	// "protect"
	return func(network, address string, conn syscall.RawConn) error {
		return control.Raw(conn, func(fd uintptr) error {
			return intfBox.AutoDetectInterfaceControl(int32(fd))
		})
	}
}

func (w *boxPlatformInterfaceWrapper) OpenTun(options *tun.Options, platformOptions option.TunPlatformOptions) (tun.Tun, error) {
	if len(options.IncludeUID) > 0 || len(options.ExcludeUID) > 0 {
		return nil, E.New("android: unsupported uid options")
	}
	if len(options.IncludeAndroidUser) > 0 {
		return nil, E.New("android: unsupported android_user option")
	}
	a, _ := json.Marshal(options)
	b, _ := json.Marshal(platformOptions)
	tunFd, err := intfBox.OpenTun(string(a), string(b))
	if err != nil {
		return nil, E.Cause(err, "intfBox.OpenTun")
	}
	// Do you want to close it?
	tunFd, err = syscall.Dup(tunFd)
	if err != nil {
		return nil, E.Cause(err, "syscall.Dup")
	}
	//
	options.FileDescriptor = tunFd
	return tun.New(*options)
}

func (w *boxPlatformInterfaceWrapper) CloseTun() error {
	return nil
}

func (w *boxPlatformInterfaceWrapper) UsePlatformDefaultInterfaceMonitor() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) CreateDefaultInterfaceMonitor(l logger.Logger) tun.DefaultInterfaceMonitor {
	return &interfaceMonitor{
		boxPlatformInterfaceWrapper: w,
		defaultInterfaceIndex:       -1,
	}
}

func (w *boxPlatformInterfaceWrapper) UsePlatformInterfaceGetter() bool {
	if intfBox != nil {
		return intfBox.UsePlatformInterfaceGetter()
	}
	return false
}

func (w *boxPlatformInterfaceWrapper) Interfaces() ([]control.Interface, error) {
	if intfBox != nil {
		interfaceIterator, err := intfBox.GetInterfaces()
		if err != nil {
			return nil, err
		}
		var interfaces []control.Interface
		for _, netInterface := range iteratorToArray[*NetworkInterface](interfaceIterator) {
			interfaces = append(interfaces, control.Interface{
				Index:     int(netInterface.Index),
				MTU:       int(netInterface.MTU),
				Name:      netInterface.Name,
				Addresses: common.Map(iteratorToArray[string](netInterface.Addresses), netip.MustParsePrefix),
			})
		}
		return interfaces, nil
	}
	return nil, E.New("not found intfBox")
}

// Android not using

func (w *boxPlatformInterfaceWrapper) UnderNetworkExtension() bool {
	return false
}
func (w *boxPlatformInterfaceWrapper) IncludeAllNetworks() bool {
	// https://sing-box.sagernet.org/manual/misc/tunnelvision/#android
	return false
}

func (w *boxPlatformInterfaceWrapper) ClearDNSCache() {
}

// process.Searcher

func (w *boxPlatformInterfaceWrapper) FindProcessInfo(ctx context.Context, network string, source netip.AddrPort, destination netip.AddrPort) (*process.Info, error) {
	var uid int32
	if useProcfs {
		uid = procfs.ResolveSocketByProcSearch(network, source, destination)
		if uid == -1 {
			return nil, E.New("procfs: not found")
		}
	} else {
		var ipProtocol int32
		switch N.NetworkName(network) {
		case N.NetworkTCP:
			ipProtocol = syscall.IPPROTO_TCP
		case N.NetworkUDP:
			ipProtocol = syscall.IPPROTO_UDP
		default:
			return nil, E.New("unknown network: ", network)
		}
		var err error
		uid, err = intfBox.FindConnectionOwner(ipProtocol, source.Addr().String(), int32(source.Port()), destination.Addr().String(), int32(destination.Port()))
		if err != nil {
			return nil, err
		}
	}
	packageName, _ := intfBox.PackageNameByUid(uid)
	return &process.Info{UserId: uid, PackageName: packageName}, nil
}
