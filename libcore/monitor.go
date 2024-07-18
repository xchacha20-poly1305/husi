package libcore

import (
	"net"
	"net/netip"
	"sync"

	"github.com/sagernet/sing-box/log"
	tun "github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/common/x/list"
)

var (
	_ tun.DefaultInterfaceMonitor = (*interfaceMonitor)(nil)
	_ InterfaceUpdateListener     = (*interfaceMonitor)(nil)
)

type interfaceMonitor struct {
	*boxPlatformInterfaceWrapper
	networkAddresses      []networkAddress
	defaultInterfaceName  string
	defaultInterfaceIndex int
	element               *list.Element[tun.NetworkUpdateCallback]
	access                sync.Mutex
	callbacks             list.List[tun.DefaultInterfaceUpdateCallback]
}

type networkAddress struct {
	interfaceName  string
	interfaceIndex int
	addresses      []netip.Prefix
}

func (m *interfaceMonitor) Start() error {
	return m.iif.StartDefaultInterfaceMonitor(m)
}

func (m *interfaceMonitor) Close() error {
	return m.iif.CloseDefaultInterfaceMonitor(m)
}

func (m *interfaceMonitor) DefaultInterfaceName(destination netip.Addr) string {
	for _, address := range m.networkAddresses {
		for _, prefix := range address.addresses {
			if prefix.Contains(destination) {
				return address.interfaceName
			}
		}
	}
	return m.defaultInterfaceName
}

func (m *interfaceMonitor) DefaultInterfaceIndex(destination netip.Addr) int {
	for _, address := range m.networkAddresses {
		for _, prefix := range address.addresses {
			if prefix.Contains(destination) {
				return address.interfaceIndex
			}
		}
	}
	return m.defaultInterfaceIndex
}

func (m *interfaceMonitor) DefaultInterface(destination netip.Addr) (string, int) {
	for _, address := range m.networkAddresses {
		for _, prefix := range address.addresses {
			if prefix.Contains(destination) {
				return address.interfaceName, address.interfaceIndex
			}
		}
	}
	return m.defaultInterfaceName, m.defaultInterfaceIndex
}

func (m *interfaceMonitor) OverrideAndroidVPN() bool {
	return false
}

func (m *interfaceMonitor) AndroidVPNEnabled() bool {
	return false
}

func (m *interfaceMonitor) RegisterCallback(callback tun.DefaultInterfaceUpdateCallback) *list.Element[tun.DefaultInterfaceUpdateCallback] {
	m.access.Lock()
	defer m.access.Unlock()
	return m.callbacks.PushBack(callback)
}

func (m *interfaceMonitor) UnregisterCallback(element *list.Element[tun.DefaultInterfaceUpdateCallback]) {
	m.access.Lock()
	defer m.access.Unlock()
	m.callbacks.Remove(element)
}

func (m *interfaceMonitor) UpdateDefaultInterface(interfaceName string, interfaceIndex32 int32) {
	if interfaceName == "" || interfaceIndex32 == -1 {
		m.defaultInterfaceName = ""
		m.defaultInterfaceIndex = -1
		m.access.Lock()
		callbacks := m.callbacks.Array()
		m.access.Unlock()
		for _, callback := range callbacks {
			callback(tun.EventNoRoute)
		}
		return
	}
	var err error
	if m.UsePlatformInterfaceGetter() {
		err = m.updateInterfacesPlatform()
	} else {
		err = m.updateInterfaces()
	}
	if err == nil {
		err = m.router.UpdateInterfaces()
	}
	if err != nil {
		log.Error(E.Cause(err, "update interfaces"))
	}
	interfaceIndex := int(interfaceIndex32)
	if m.defaultInterfaceName == interfaceName && m.defaultInterfaceIndex == interfaceIndex {
		return
	}
	m.defaultInterfaceName = interfaceName
	m.defaultInterfaceIndex = interfaceIndex
	m.access.Lock()
	callbacks := m.callbacks.Array()
	m.access.Unlock()
	for _, callback := range callbacks {
		callback(tun.EventInterfaceUpdate)
	}
}

func (m *interfaceMonitor) updateInterfaces() error {
	interfaces, err := net.Interfaces()
	if err != nil {
		return err
	}
	var addresses []networkAddress
	for _, iif := range interfaces {
		var netAddresses []net.Addr
		netAddresses, err = iif.Addrs()
		if err != nil {
			return err
		}
		var address networkAddress
		address.interfaceName = iif.Name
		address.interfaceIndex = iif.Index
		address.addresses = common.Map(common.FilterIsInstance(netAddresses, func(it net.Addr) (*net.IPNet, bool) {
			value, loaded := it.(*net.IPNet)
			return value, loaded
		}), func(it *net.IPNet) netip.Prefix {
			bits, _ := it.Mask.Size()
			return netip.PrefixFrom(M.AddrFromIP(it.IP), bits)
		})
		addresses = append(addresses, address)
	}
	m.networkAddresses = addresses
	return nil
}

func (m *interfaceMonitor) updateInterfacesPlatform() error {
	interfaces, err := m.Interfaces()
	if err != nil {
		return err
	}
	var addresses []networkAddress
	for _, iif := range interfaces {
		var address networkAddress
		address.interfaceName = iif.Name
		address.interfaceIndex = iif.Index
		// address.addresses = common.Map(iif.Addresses, netip.MustParsePrefix)
		addresses = append(addresses, address)
	}
	m.networkAddresses = addresses
	return nil
}
