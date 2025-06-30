// Package http implements wrapped http outbound that support UDP over TCP.
package http

import (
	"context"
	"net"
	"os"
	"strings"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/outbound"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/protocol/http"
	"github.com/sagernet/sing/common"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/uot"

	"libcore/plugin/pluginoption"
)

func RegisterOutbound(registry *outbound.Registry) {
	outbound.Register[pluginoption.HTTPOutboundOptions](registry, C.TypeHTTP, NewOutbound)
}

type Outbound struct {
	*http.Outbound
	logger    log.ContextLogger
	uotClient *uot.Client
}

func NewOutbound(ctx context.Context, router adapter.Router, logger log.ContextLogger, tag string, options pluginoption.HTTPOutboundOptions) (adapter.Outbound, error) {
	httpOutbound, err := http.NewOutbound(ctx, router, logger, tag, options.HTTPOutboundOptions)
	if err != nil {
		return nil, err
	}
	packetableOutbound := &Outbound{
		Outbound: httpOutbound.(*http.Outbound),
		logger:   logger,
	}
	uotOptions := common.PtrValueOrDefault(options.UDPOverTCP)
	if uotOptions.Enabled {
		packetableOutbound.uotClient = &uot.Client{
			Dialer:  packetableOutbound,
			Version: uotOptions.Version,
		}
	}
	return packetableOutbound, nil
}

func (o *Outbound) DialContext(ctx context.Context, network string, destination M.Socksaddr) (net.Conn, error) {
	if o.uotClient != nil && strings.HasPrefix(network, N.NetworkUDP) {
		o.logger.InfoContext(ctx, "outbound UoT connection to ", destination)
		return o.uotClient.DialContext(ctx, network, destination)
	}
	return o.Outbound.DialContext(ctx, network, destination)
}

func (o *Outbound) ListenPacket(ctx context.Context, destination M.Socksaddr) (net.PacketConn, error) {
	if o.uotClient != nil {
		o.logger.InfoContext(ctx, "outbound UoT packet connection to ", destination)
		return o.uotClient.ListenPacket(ctx, destination)
	}
	return nil, os.ErrInvalid
}

func (o *Outbound) Network() []string {
	networks := []string{N.NetworkTCP}
	if o.uotClient != nil {
		networks = append(networks, N.NetworkUDP)
	}
	return networks
}
