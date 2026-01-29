package trusttunnel

import (
	"context"
	"net"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/common/dialer"
	"github.com/sagernet/sing-box/common/tls"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/auth"
	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

	"libcore/plugin/pluginoption"

	"github.com/xchacha20-poly1305/sing-trusttunnel"
)

func init() {
	trusttunnel.ErrQUICNotIncluded = C.ErrQUICNotIncluded
}

func RegisterOutbound(registry *outbound.Registry) {
	outbound.Register[pluginoption.TrustTunnelOutboundOptions](registry, pluginoption.TypeTrustTunnel, NewOutbound)
}

type Outbound struct {
	outbound.Adapter
	ctx    context.Context
	logger log.ContextLogger
	client *trusttunnel.Client
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
	client, err := trusttunnel.NewClient(trusttunnel.ClientOptions{
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
	})
	if err != nil {
		return nil, err
	}
	networks := []string{N.NetworkTCP, N.NetworkUDP}
	if withGvisor {
		networks = append(networks, N.NetworkICMP)
	}
	return &Outbound{
		Adapter: outbound.NewAdapterWithDialerOptions(pluginoption.TypeTrustTunnel, tag, networks, options.DialerOptions),
		ctx:     ctx,
		logger:  logger,
		client:  client,
	}, nil
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

func (h *Outbound) Close() error {
	return common.Close(
		common.PtrOrNil(h.client),
	)
}
