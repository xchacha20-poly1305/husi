package libcore

import (
	"net/netip"
	"sync"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	N "github.com/sagernet/sing/common/network"

	"libcore/procfs"
	"libcore/protect"

	"golang.org/x/sys/unix"
)

type boxPlatformInterfaceWrapper struct {
	useProcFS              bool // Store iif.UseProcFS()
	iif                    PlatformInterface
	networkManager         adapter.NetworkManager
	myTunName              string
	defaultInterfaceAccess sync.Mutex
	defaultInterface       *control.Interface
	forTest                bool

	// Set by interface monitor, which can't provide these information on Android.
	/*isExpensive            bool
	isConstrained          bool*/
}

var _ adapter.PlatformInterface = (*boxPlatformInterfaceWrapper)(nil)

type WIFIState interface {
	GetSSID() string
	GetBSSID() string
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
	ok := w.iif.AutoDetectInterfaceControl(int32(fd))
	if !ok {
		_ = protect.Protect(ProtectPath, fd)
	}
	return nil
}

func (w *boxPlatformInterfaceWrapper) UsePlatformInterface() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) OpenInterface(options *tun.Options, platformOptions option.TunPlatformOptions) (tun.Tun, error) {
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
	options.Name, err = tunnelName(tunFd)
	if err != nil {
		return nil, E.Cause(err, "tunnelName")
	}
	options.InterfaceMonitor.RegisterMyInterface(options.Name)
	dupFd, err := unix.Dup(int(tunFd))
	if err != nil {
		return nil, E.Cause(err, "unix.Dup")
	}
	options.FileDescriptor = dupFd
	w.myTunName = options.Name
	return tun.New(*options)
}

func (w *boxPlatformInterfaceWrapper) UsePlatformDefaultInterfaceMonitor() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) CreateDefaultInterfaceMonitor(logger logger.Logger) tun.DefaultInterfaceMonitor {
	return &interfaceMonitor{
		boxPlatformInterfaceWrapper: w,
		logger:                      logger,
	}
}

func (w *boxPlatformInterfaceWrapper) UsePlatformNetworkInterfaces() bool {
	return true
}

func (w *boxPlatformInterfaceWrapper) NetworkInterfaces() ([]adapter.NetworkInterface, error) {
	interfaceIterator, err := w.iif.GetInterfaces()
	if err != nil {
		return nil, err
	}
	interfaces := make([]adapter.NetworkInterface, 0, interfaceIterator.Length())
	for interfaceIterator.HasNext() {
		netInterface := interfaceIterator.Next()
		if netInterface.Name == w.myTunName {
			continue
		}
		// w.defaultInterfaceAccess.Lock()
		// isDefault := w.defaultInterface != nil && int(netInterface.Index) == w.defaultInterface.Index
		// w.defaultInterfaceAccess.Unlock()
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
	interfaces = common.UniqBy(interfaces, func(it adapter.NetworkInterface) string {
		return it.Name
	})
	return interfaces, nil
}

func (w *boxPlatformInterfaceWrapper) RequestPermissionForWIFIState() error {
	// Even not be implemented or invoked in sing-box
	return nil
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

// Process Searcher

func (w *boxPlatformInterfaceWrapper) UsePlatformConnectionOwnerFinder() bool {
	return true
}

type ConnectionOwner struct {
	UserId             int32
	AndroidPackageName string
}

func NewConnectionOwner(userId int32, androidPackageName string) *ConnectionOwner {
	return &ConnectionOwner{
		UserId:             userId,
		AndroidPackageName: androidPackageName,
	}
}

func (w *boxPlatformInterfaceWrapper) FindConnectionOwner(request *adapter.FindConnectionOwnerRequest) (*adapter.ConnectionOwner, error) {
	if w.useProcFS {
		var source netip.AddrPort
		var destination netip.AddrPort
		sourceAddr, _ := netip.ParseAddr(request.SourceAddress)
		source = netip.AddrPortFrom(sourceAddr, uint16(request.SourcePort))
		destAddr, _ := netip.ParseAddr(request.DestinationAddress)
		destination = netip.AddrPortFrom(destAddr, uint16(request.DestinationPort))

		var network string
		switch request.IpProtocol {
		case int32(unix.IPPROTO_TCP):
			network = N.NetworkTCP
		case int32(unix.IPPROTO_UDP):
			network = N.NetworkUDP
		default:
			return nil, E.New("unknown protocol: ", request.IpProtocol)
		}

		uid := procfs.ResolveSocketByProcSearch(network, source, destination)
		if uid == -1 {
			return nil, E.New("procfs: not found")
		}
		return &adapter.ConnectionOwner{
			UserId: uid,
		}, nil
	}

	result, err := w.iif.FindConnectionOwner(request.IpProtocol, request.SourceAddress, request.SourcePort, request.DestinationAddress, request.DestinationPort)
	if err != nil {
		return nil, err
	}
	return &adapter.ConnectionOwner{
		UserId:             result.UserId,
		AndroidPackageName: result.AndroidPackageName,
		// ProcessPath: result.ProcessPath, // Not available in Android
	}, nil
}

func (w *boxPlatformInterfaceWrapper) UsePlatformWIFIMonitor() bool {
	return true
}

// Android not using

func (w *boxPlatformInterfaceWrapper) UnderNetworkExtension() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) NetworkExtensionIncludeAllNetworks() bool {
	// https://sing-box.sagernet.org/manual/misc/tunnelvision/#android
	return false
}

func (w *boxPlatformInterfaceWrapper) ClearDNSCache() {
}

func (w *boxPlatformInterfaceWrapper) UsePlatformNotification() bool {
	return false
}

func (w *boxPlatformInterfaceWrapper) SendNotification(_ *adapter.Notification) error {
	return nil
}

func (w *boxPlatformInterfaceWrapper) SystemCertificates() []string {
	// Already set in certs.go
	return nil
}
