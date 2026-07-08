package trusttunnel

import (
	"context"
	"net"
	"net/netip"
	"os"
	"slices"

	"libcore/plugin/pluginoption"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/common/dialer"
	"github.com/sagernet/sing-box/common/tls"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/auth"
	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/sing-trusttunnel"
)

func init() {
	trusttunnel.ErrQUICNotIncluded = C.ErrQUICNotIncluded
}

func RegisterOutbound(registry *outbound.Registry) {
	outbound.Register[pluginoption.TrustTunnelOutboundOptions](registry, pluginoption.TypeTrustTunnel, NewOutbound)
}

var _ adapter.FlowOutbound = (*Outbound)(nil)

type Outbound struct {
	outbound.Adapter
	ctx       context.Context
	logger    log.ContextLogger
	dnsRouter adapter.DNSRouter
	client    *trusttunnel.Client
	icmpPort  *icmpPort
}

func NewOutbound(ctx context.Context, router adapter.Router, logger log.ContextLogger, tag string, options pluginoption.TrustTunnelOutboundOptions) (adapter.Outbound, error) {
	if options.TLS == nil || !options.TLS.Enabled {
		return nil, C.ErrTLSRequired
	}
	if options.Username == "" {
		return nil, E.New("require auth")
	}
	detour, err := dialer.New(ctx, options.DialerOptions, options.ServerIsDomain())
	if err != nil {
		return nil, err
	}
	server := options.ServerOptions.Build()
	tlsConfig, err := tls.NewClient(ctx, logger, server.String(), *options.TLS)
	if err != nil {
		return nil, err
	}
	dnsRouter := service.FromContext[adapter.DNSRouter](ctx)
	client, err := trusttunnel.NewClient(trusttunnel.ClientOptions{
		Ctx:    ctx,
		Detour: detour,
		Server: server,
		Auth: auth.User{
			Username: options.Username,
			Password: options.Password,
		},
		TLSConfig:             tlsConfig,
		QUIC:                  options.QUIC,
		QUICCongestionControl: options.QUICCongestionControl,
		HealthCheck:           options.HealthCheck,
		ResolveFunc: func(fqdn string) (netip.Addr, error) {
			addresses, lookupErr := dnsRouter.Lookup(ctx, fqdn, adapter.DNSQueryOptions{})
			if lookupErr != nil {
				return netip.Addr{}, lookupErr
			}
			return addresses[0], nil
		},
	})
	if err != nil {
		return nil, err
	}
	networks := options.Network.Build()
	var port *icmpPort
	if slices.Contains(networks, N.NetworkICMP) {
		port = newICMPPort(ctx, logger, client, 0)
	}
	return &Outbound{
		Adapter:   outbound.NewAdapterWithDialerOptions(pluginoption.TypeTrustTunnel, tag, networks, options.DialerOptions),
		ctx:       ctx,
		logger:    logger,
		dnsRouter: dnsRouter,
		client:    client,
		icmpPort:  port,
	}, nil
}

func (h *Outbound) Start(stage adapter.StartStage) error {
	if stage != adapter.StartStateStart {
		return nil
	}
	return h.client.Start()
}

func (h *Outbound) DialContext(ctx context.Context, network string, destination M.Socksaddr) (net.Conn, error) {
	switch network {
	case N.NetworkTCP:
		ctx, metadata := adapter.ExtendContext(ctx)
		metadata.Outbound = h.Tag()
		metadata.Destination = destination
		h.logger.InfoContext(ctx, "outbound connection to ", destination)
		return h.client.Dial(ctx, destination)
	case N.NetworkUDP:
		if destination.IsDomain() {
			addresses, err := h.dnsRouter.Lookup(ctx, destination.Fqdn, adapter.DNSQueryOptions{})
			if err != nil {
				return nil, err
			}
			destination = M.Socksaddr{
				Addr: addresses[0],
				Port: destination.Port,
			}
		}
		packetConn, err := h.ListenPacket(ctx, destination)
		if err != nil {
			return nil, err
		}
		return bufio.NewBindPacketConn(packetConn, destination), nil
	default:
		return nil, E.Extend(N.ErrUnknownNetwork, network)
	}
}

func (h *Outbound) ListenPacket(ctx context.Context, destination M.Socksaddr) (net.PacketConn, error) {
	ctx, metadata := adapter.ExtendContext(ctx)
	metadata.Outbound = h.Tag()
	metadata.Destination = destination
	h.logger.InfoContext(ctx, "outbound packet connection to ", destination)
	return h.client.ListenPacket(ctx)
}

func (h *Outbound) InterfaceUpdated() {
	h.client.ResetConnections()
}

func (h *Outbound) PreMatchFlow(network string, destination netip.Addr) adapter.PreMatchAction {
	if network == N.NetworkICMP && h.icmpPort != nil {
		return adapter.PreMatchFlow
	}
	return adapter.PreMatchContinue
}

func (h *Outbound) PortAddresses() (netip.Addr, netip.Addr) {
	if h.icmpPort == nil {
		return netip.Addr{}, netip.Addr{}
	}
	return h.icmpPort.PortAddresses()
}

func (h *Outbound) PortMTU() uint32 {
	if h.icmpPort == nil {
		return 0
	}
	return h.icmpPort.PortMTU()
}

func (h *Outbound) AttachReturn(returnPath tun.Return) error {
	if h.icmpPort == nil {
		return os.ErrInvalid
	}
	return h.icmpPort.AttachReturn(returnPath)
}

func (h *Outbound) DetachReturn(returnPath tun.Return) error {
	if h.icmpPort == nil {
		return os.ErrInvalid
	}
	return h.icmpPort.DetachReturn(returnPath)
}

func (h *Outbound) WritePackets(packets [][]byte) error {
	if h.icmpPort == nil {
		return os.ErrInvalid
	}
	return h.icmpPort.WritePackets(packets)
}

func (h *Outbound) Close() error {
	return common.Close(
		common.PtrOrNil(h.icmpPort),
		common.PtrOrNil(h.client),
	)
}
