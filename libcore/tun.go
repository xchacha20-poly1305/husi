package libcore

import (
	"net"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common/control"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	"github.com/sagernet/sing/common/x/list"

	"golang.org/x/sys/unix"
)

var (
	_ tun.DefaultInterfaceMonitor = (*interfaceMonitor)(nil)
	_ InterfaceUpdateListener     = (*interfaceMonitor)(nil)
)

type InterfaceUpdateListener interface {
	UpdateDefaultInterface(interfaceName string, interfaceIndex int32)
}

const (
	InterfaceTypeWIFI     = int32(C.InterfaceTypeWIFI)
	InterfaceTypeCellular = int32(C.InterfaceTypeCellular)
	InterfaceTypeEthernet = int32(C.InterfaceTypeEthernet)
	InterfaceTypeOther    = int32(C.InterfaceTypeOther)
)

type NetworkInterface struct {
	Index     int32
	MTU       int32
	Name      string
	Addresses StringIterator
	Flags     int32

	Type      int32
	DNSServer StringIterator
	Metered   bool
}

type NetworkInterfaceIterator interface {
	Next() *NetworkInterface
	HasNext() bool
	Length() int32
}

type interfaceMonitor struct {
	*boxPlatformInterfaceWrapper
	element   *list.Element[tun.NetworkUpdateCallback]
	callbacks list.List[tun.DefaultInterfaceUpdateCallback]
	logger    logger.Logger
}

func (m *interfaceMonitor) Start() error {
	if m.forTest {
		// Just make dialer has available interface.
		return m.networkManager.UpdateInterfaces()
	}
	return m.iif.StartDefaultInterfaceMonitor(m)
}

func (m *interfaceMonitor) Close() error {
	if m.forTest {
		return nil
	}
	return m.iif.CloseDefaultInterfaceMonitor(m)
}

func (m *interfaceMonitor) DefaultInterface() *control.Interface {
	m.defaultInterfaceAccess.Lock()
	defer m.defaultInterfaceAccess.Unlock()
	return m.defaultInterface
}

func (m *interfaceMonitor) OverrideAndroidVPN() bool {
	return false
}

func (m *interfaceMonitor) AndroidVPNEnabled() bool {
	return false
}

func (m *interfaceMonitor) RegisterCallback(callback tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	m.defaultInterfaceAccess.Lock()
	defer m.defaultInterfaceAccess.Unlock()
	return m.callbacks.PushBack(callback)
}

func (m *interfaceMonitor) UnregisterCallback(element *list.Element[tun.DefaultInterfaceUpdateCallback]) {
	m.defaultInterfaceAccess.Lock()
	defer m.defaultInterfaceAccess.Unlock()
	m.callbacks.Remove(element)
}

func (m *interfaceMonitor) UpdateDefaultInterface(interfaceName string, interfaceIndex32 int32) {
	/*m.isExpensive = isExpensive
	m.isConstrained = isConstrained*/
	err := m.networkManager.UpdateInterfaces()
	if err != nil {
		m.logger.Error(E.Cause(err, "update interfaces"))
	}
	m.defaultInterfaceAccess.Lock()
	if interfaceIndex32 == -1 {
		m.defaultInterface = nil
		callbacks := m.callbacks.Array()
		m.defaultInterfaceAccess.Unlock()
		for _, callback := range callbacks {
			callback(nil, 0)
		}
		return
	}
	oldInterface := m.defaultInterface
	newInterface, err := m.networkManager.InterfaceFinder().ByIndex(int(interfaceIndex32))
	if err != nil {
		m.defaultInterfaceAccess.Unlock()
		m.logger.Error(E.Cause(err, "find updated interface: ", interfaceName))
		return
	}
	m.defaultInterface = newInterface
	if oldInterface != nil && oldInterface.Name == m.defaultInterface.Name && oldInterface.Index == m.defaultInterface.Index {
		m.defaultInterfaceAccess.Unlock()
		return
	}
	callbacks := m.callbacks.Array()
	m.defaultInterfaceAccess.Unlock()
	for _, callback := range callbacks {
		callback(newInterface, 0)
	}
}

// copied from net.linkFlags
func linkFlags(rawFlags uint32) net.Flags {
	var f net.Flags
	if rawFlags&unix.IFF_UP != 0 {
		f |= net.FlagUp
	}
	if rawFlags&unix.IFF_RUNNING != 0 {
		f |= net.FlagRunning
	}
	if rawFlags&unix.IFF_BROADCAST != 0 {
		f |= net.FlagBroadcast
	}
	if rawFlags&unix.IFF_LOOPBACK != 0 {
		f |= net.FlagLoopback
	}
	if rawFlags&unix.IFF_POINTOPOINT != 0 {
		f |= net.FlagPointToPoint
	}
	if rawFlags&unix.IFF_MULTICAST != 0 {
		f |= net.FlagMulticast
	}
	return f
}
