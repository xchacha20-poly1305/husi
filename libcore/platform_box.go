package libcore

import (
	"context"
	"net/netip"
	"os"
	"sync"
	"syscall"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/process"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/libbox/platform"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	N "github.com/sagernet/sing/common/network"

	"libcore/procfs"

	"golang.org/x/sys/unix"
)

type boxPlatformInterfaceWrapper struct {
	useProcFS              bool // Store iif.UseProcFS()
	iif                    PlatformInterface
	networkManager         adapter.NetworkManager
	myTunName              string
	defaultInterfaceAccess sync.Mutex
	defaultInterface       *control.Interface

	// Set by interface monitor, which can't provide these information on Android.
	/*isExpensive            bool
	isConstrained          bool*/
}

var _ platform.Interface = (*boxPlatformInterfaceWrapper)(nil)

type WIFIState interface {
	GetSSID() string
	GetBSSID() string
}

func (w *boxPlatformInterfaceWrapper) ReadWIFIState() adapter.WIFIState {
	wifiState := w.iif.ReadWIFIState()
	if wifiState == nil {
		return adapter.WIFIState{}
	}
	return adapter.WIFIState{
		SSID:  wifiState.GetSSID(),
		BSSID: wifiState.GetBSSID(),
	}
}

func (w *boxPlatformInterfaceWrapper) Initialize(networkManager adapter.NetworkManager) error {
	w.networkManager = networkManager
	return nil
}

func (w *boxPlatformInterfaceWrapper) UsePlatformAutoDetectInterfaceControl() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) AutoDetectInterfaceControl(fd int) error {
	// "protect"
	return w.iif.AutoDetectInterfaceControl(int32(fd))
}

func (w *boxPlatformInterfaceWrapper) OpenTun(options *tun.Options, platformOptions option.TunPlatformOptions) (tun.Tun, error) {
	if len(options.IncludeUID) > 0 || len(options.ExcludeUID) > 0 {
		return nil, E.New("android: unsupported uid options")
	}
	if len(options.IncludeAndroidUser) > 0 {
		return nil, E.New("android: unsupported android_user option")
	}
	tunFd, err := w.iif.OpenTun()
	if err != nil {
		return nil, E.Cause(err, "iif.OpenTun")
	}
	// Do you want to close it?
	tunFd, err = unix.Dup(tunFd)
	if err != nil {
		return nil, E.Cause(err, "syscall.Dup")
	}
	//
	options.FileDescriptor = tunFd
	w.myTunName = options.Name
	return tun.New(*options)
}

func (w *boxPlatformInterfaceWrapper) CreateDefaultInterfaceMonitor(logger logger.Logger) tun.DefaultInterfaceMonitor {
	return &interfaceMonitor{
		boxPlatformInterfaceWrapper: w,
		logger:                      logger,
	}
}

func (w *boxPlatformInterfaceWrapper) Interfaces() ([]adapter.NetworkInterface, error) {
	interfaceIterator, err := w.iif.GetInterfaces()
	if err != nil {
		return nil, err
	}
	interfaces := make([]adapter.NetworkInterface, 0, interfaceIterator.Length())
	for netInterface := interfaceIterator.Next(); interfaceIterator.HasNext(); netInterface = interfaceIterator.Next() {
		if netInterface.Name == w.myTunName {
			continue
		}
		w.defaultInterfaceAccess.Lock()
		// isDefault := w.defaultInterface != nil && int(netInterface.Index) == w.defaultInterface.Index
		w.defaultInterfaceAccess.Unlock()
		interfaces = append(interfaces, adapter.NetworkInterface{
			Interface: control.Interface{
				Index:     int(netInterface.Index),
				MTU:       int(netInterface.MTU),
				Name:      netInterface.Name,
				Addresses: common.Map(iteratorToArray[string](netInterface.Addresses), netip.MustParsePrefix),
				Flags:     linkFlags(uint32(netInterface.Flags)),
			},
			Type:        C.InterfaceType(netInterface.Type),
			DNSServers:  iteratorToArray[string](netInterface.DNSServer),
			Expensive:   netInterface.Metered, /*|| isDefault && w.isExpensive*/
			Constrained: false,                // Not for Android
		})
	}
	return interfaces, nil
}

// process.Searcher

func (w *boxPlatformInterfaceWrapper) FindProcessInfo(_ context.Context, network string, source netip.AddrPort, destination netip.AddrPort) (*process.Info, error) {
	var uid int32
	if w.useProcFS {
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
		uid, err = w.iif.FindConnectionOwner(ipProtocol, source.Addr().String(), int32(source.Port()), destination.Addr().String(), int32(destination.Port()))
		if err != nil {
			return nil, err
		}
	}
	packageName, _ := w.iif.PackageNameByUid(uid)
	return &process.Info{UserId: uid, PackageName: packageName}, nil
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

func (w *boxPlatformInterfaceWrapper) SendNotification(_ *platform.Notification) error {
	return nil
}

func (w *boxPlatformInterfaceWrapper) UpdateRouteOptions(options *tun.Options, platformOptions option.TunPlatformOptions) error {
	// Some *table rules, can't use for Android
	return os.ErrInvalid
}

func (w *boxPlatformInterfaceWrapper) CloseTun() error {
	// Close by core
	return nil
}
