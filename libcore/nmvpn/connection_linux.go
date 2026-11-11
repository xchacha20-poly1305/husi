//go:build linux && !android

package nmvpn

import (
	"context"
	"encoding/binary"
	"net/netip"
	"os"
	"strconv"
	"time"

	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/godbus/dbus/v5"
	"golang.org/x/sys/unix"
)

const (
	managerName                   = "org.freedesktop.NetworkManager"
	connectionActivated    uint32 = 2
	connectionDeactivating uint32 = 3
	connectionDeactivated  uint32 = 4
)

func connectionSettings(options *tun.Options) (map[string]map[string]dbus.Variant, error) {
	if options.NetNs != "" {
		return nil, E.New("NetworkManager TUN does not support network namespaces")
	}
	if options.DNSModeOrDefault() == tun.DNSModeHijack && len(options.DNSAddress) != 0 {
		return nil, E.New("NetworkManager TUN requires implicit DNS hijack addresses with the pinned core")
	}
	if options.AutoRoute || options.AutoRedirectMarkMode || options.MultiQueue {
		return nil, E.New("NetworkManager TUN requires auto_route, auto_redirect and multi_queue to be disabled")
	}
	if len(options.Inet4RouteExcludeAddress)+len(options.Inet6RouteExcludeAddress)+len(options.IncludeUID)+len(options.ExcludeUID)+len(options.IncludeInterface)+len(options.ExcludeInterface)+len(options.IncludeMACAddress)+len(options.ExcludeMACAddress) != 0 {
		return nil, E.New("NetworkManager TUN does not support route exclusions or UID filters")
	}
	settings := map[string]map[string]dbus.Variant{
		"connection": {
			"id":             dbus.MakeVariant(options.Name),
			"type":           dbus.MakeVariant("tun"),
			"interface-name": dbus.MakeVariant(options.Name),
			"autoconnect":    dbus.MakeVariant(false),
		},
		"tun": {
			"mode":        dbus.MakeVariant(uint32(1)),
			"owner":       dbus.MakeVariant(strconv.Itoa(os.Getuid())),
			"pi":          dbus.MakeVariant(false),
			"vnet-hdr":    dbus.MakeVariant(false),
			"multi-queue": dbus.MakeVariant(false), // sing-tun does not support multi-queue with platform interface
		},
		"802-3-ethernet": {
			"mtu": dbus.MakeVariant(options.MTU),
		},
	}
	for _, family := range []struct {
		key       string
		addresses []netip.Prefix
		routes    []netip.Prefix
		dns       func() ([]netip.Addr, error)
	}{
		{"ipv4", options.Inet4Address, options.Inet4RouteAddress, options.Inet4DNSAddress},
		{"ipv6", options.Inet6Address, options.Inet6RouteAddress, options.Inet6DNSAddress},
	} {
		config := map[string]dbus.Variant{"method": dbus.MakeVariant("disabled")}
		settings[family.key] = config
		if len(family.addresses) == 0 {
			continue
		}
		config["method"] = dbus.MakeVariant("manual")
		addresses := make([]map[string]dbus.Variant, 0, len(family.addresses))
		for _, prefix := range family.addresses {
			addresses = append(addresses, map[string]dbus.Variant{
				"address": dbus.MakeVariant(prefix.Addr().String()),
				"prefix":  dbus.MakeVariant(uint32(prefix.Bits())),
			})
		}
		config["address-data"] = dbus.MakeVariant(addresses)
		routes := family.routes
		if len(routes) == 0 {
			if family.key == "ipv4" {
				routes = []netip.Prefix{netip.MustParsePrefix("0.0.0.0/0")}
			} else {
				routes = []netip.Prefix{netip.MustParsePrefix("::/0")}
			}
		}
		routeData := make([]map[string]dbus.Variant, 0, len(routes))
		for _, prefix := range routes {
			routeData = append(routeData, map[string]dbus.Variant{
				"dest":   dbus.MakeVariant(prefix.Masked().Addr().String()),
				"prefix": dbus.MakeVariant(uint32(prefix.Bits())),
			})
		}
		config["route-data"] = dbus.MakeVariant(routeData)
		config["route-metric"] = dbus.MakeVariant(int64(50))
		config["ignore-auto-dns"] = dbus.MakeVariant(true)
		if options.DNSModeOrDefault() != tun.DNSModeDisabled {
			servers, err := family.dns()
			if err != nil {
				return nil, err
			}
			if family.key == "ipv4" {
				values := common.Map(servers, ip4Number)
				config["dns"] = dbus.MakeVariant(values)
			} else {
				values := common.Map(servers, netip.Addr.AsSlice)
				config["dns"] = dbus.MakeVariant(values)
			}
			config["dns-priority"] = dbus.MakeVariant(int32(-50))
		}
	}
	return settings, nil
}

func (p *DbusTun) OpenTun(options *tun.Options) (tun.Tun, error) {
	p.access.RLock()
	alreadyActive := p.connection != nil
	p.access.RUnlock()
	if alreadyActive {
		return nil, E.New("NetworkManager supports exactly one TUN inbound")
	}
	settings, err := connectionSettings(options)
	if err != nil {
		return nil, err
	}
	connection, err := dbus.ConnectSystemBus()
	if err != nil {
		return nil, E.Cause(err, "connect to NetworkManager system bus")
	}
	success := false
	defer func() {
		if !success {
			_ = connection.Close()
		}
	}()
	ctx, cancel := context.WithTimeout(p.ctx, 45*time.Second)
	defer cancel()
	var profile, active dbus.ObjectPath
	var result map[string]dbus.Variant
	err = connection.
		Object(managerName, "/org/freedesktop/NetworkManager").
		CallWithContext(ctx, managerName+".AddAndActivateConnection2", dbus.FlagAllowInteractiveAuthorization, settings, dbus.ObjectPath("/"), dbus.ObjectPath("/"), map[string]dbus.Variant{
			"persist":         dbus.MakeVariant("volatile"),
			"bind-activation": dbus.MakeVariant("dbus-client"),
		}).
		Store(&profile, &active, &result)
	if err != nil {
		return nil, E.Cause(err, "activate NetworkManager TUN")
	}
	err = waitConnection(ctx, connection, active)
	if err != nil {
		return nil, err
	}
	deviceTun, err := attachTunDevice(options)
	if err != nil {
		return nil, err
	}
	p.access.Lock()
	monitorCtx, stopMonitor := context.WithCancel(p.ctx)
	p.connection = connection
	p.stopMonitor = stopMonitor
	p.access.Unlock()
	success = true
	go p.watchConnection(monitorCtx, connection, active)
	// The go stack requires *tun.NativeTun rather than an interface wrapper.
	// The service host closes the NM connection after closing the core.
	return deviceTun, nil
}

// NetworkManager creates the TUN; attaching obtains its file descriptor.
func attachTunDevice(options *tun.Options) (tun.Tun, error) {
	fd, err := unix.Open("/dev/net/tun", unix.O_RDWR|unix.O_CLOEXEC|unix.O_NONBLOCK, 0)
	if err != nil {
		return nil, err
	}
	request, err := unix.NewIfreq(options.Name)
	if err != nil {
		_ = unix.Close(fd)
		return nil, err
	}
	request.SetUint16(unix.IFF_TUN | unix.IFF_NO_PI)
	err = unix.IoctlIfreq(fd, unix.TUNSETIFF, request)
	if err != nil {
		_ = unix.Close(fd)
		return nil, E.Cause(err, "attach to NetworkManager TUN")
	}
	options.Name = request.Name()
	options.FileDescriptor = fd
	options.GSO = false
	device, err := tun.New(*options)
	if err != nil {
		_ = unix.Close(fd)
		return nil, err
	}
	return device, nil
}

func (p *DbusTun) Close() error {
	p.access.Lock()
	defer p.access.Unlock()
	if p.stopMonitor != nil {
		p.stopMonitor()
		p.stopMonitor = nil
	}
	if p.connection != nil {
		_ = p.connection.Close()
		p.connection = nil
	}
	return nil
}

func connectionState(ctx context.Context, connection *dbus.Conn, path dbus.ObjectPath) (uint32, error) {
	var value dbus.Variant
	err := connection.
		Object(managerName, path).
		CallWithContext(ctx, "org.freedesktop.DBus.Properties.Get", 0, managerName+".Connection.Active", "State").
		Store(&value)
	if err != nil {
		return 0, err
	}
	state, isU32 := value.Value().(uint32)
	if !isU32 {
		return 0, E.New("invalid NetworkManager active connection state")
	}
	return state, nil
}

func waitConnection(ctx context.Context, connection *dbus.Conn, path dbus.ObjectPath) error {
	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()
	for {
		state, err := connectionState(ctx, connection, path)
		if err != nil {
			return err
		}
		switch state {
		case connectionActivated:
			return nil
		case connectionDeactivating, connectionDeactivated:
			return E.New("NetworkManager TUN activation failed (state ", state, ")")
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
		}
	}
}

func (p *DbusTun) watchConnection(ctx context.Context, connection *dbus.Conn, path dbus.ObjectPath) {
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			state, err := connectionState(ctx, connection, path)
			if ctx.Err() != nil {
				return
			}
			if err != nil || state != connectionActivated {
				p.access.RLock()
				if ctx.Err() == nil && p.connection == connection && p.onLost != nil {
					p.onLost()
				}
				p.access.RUnlock()
				return
			}
		}
	}
}

func ip4Number(address netip.Addr) uint32 {
	bytes := address.As4()
	// NetworkManager's legacy IPv4 D-Bus fields store network-order bytes in a native uint32.
	return binary.NativeEndian.Uint32(bytes[:])
}
