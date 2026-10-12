package protect

import (
	"os"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing/common/control"

	protectgo "github.com/xchacha20-poly1305/protect-go"
)

// networkManagerProtector exempts a socket with the interface binding and
// routing mark that sing-box applies to its own outbound sockets.
type networkManagerProtector struct {
	networkManager adapter.NetworkManager
}

func (p networkManagerProtector) Protect(fileDescriptor int) error {
	controlFunc := p.controlFunc()
	if controlFunc == nil {
		// Nothing is configured to keep sockets out of the TUN, so there is
		// nothing to apply; the socket is routed like any other.
		return nil
	}
	network, err := protectgo.SocketNetwork(fileDescriptor)
	if err != nil {
		return err
	}
	// The plugin has not dialed yet, so there is no destination address.
	return controlFunc(network, "", fdConn(fileDescriptor))
}

func (p networkManagerProtector) controlFunc() control.Func {
	defaultOptions := p.networkManager.DefaultOptions()
	var bindFunc control.Func
	if defaultOptions.BindInterface != "" {
		bindFunc = control.BindToInterface(p.networkManager.InterfaceFinder(), defaultOptions.BindInterface, -1)
	} else if p.networkManager.AutoDetectInterface() {
		bindFunc = p.networkManager.AutoDetectInterfaceFunc()
	}
	var markFunc control.Func
	if p.networkManager.AutoRedirectOutputMark() != 0 {
		markFunc = p.networkManager.AutoRedirectOutputMarkFunc()
	} else if defaultOptions.RoutingMark != 0 {
		markFunc = control.RoutingMark(defaultOptions.RoutingMark)
	}
	return control.Append(bindFunc, markFunc)
}

type fdConn int

func (c fdConn) Control(block func(fd uintptr)) error {
	block(uintptr(c))
	return nil
}

func (c fdConn) Read(func(fd uintptr) bool) error {
	return os.ErrInvalid
}

func (c fdConn) Write(func(fd uintptr) bool) error {
	return os.ErrInvalid
}
