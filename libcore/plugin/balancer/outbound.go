package balancer

import (
	"context"
	"net"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/pluginoption"
)

func RegisterOutbound(registry *outbound.Registry) {
	outbound.Register[pluginoption.BalancerOutboundOptions](registry, pluginoption.TypeBalancer, NewOutbound)
}

var (
	_ adapter.OutboundGroup           = (*Outbound)(nil)
	_ adapter.ConnectionHandler       = (*Outbound)(nil)
	_ adapter.PacketConnectionHandler = (*Outbound)(nil)
	_ adapter.Referrer                = (*Outbound)(nil)
)

type Outbound struct {
	outbound.Adapter
	ctx        context.Context
	outbound   adapter.OutboundManager
	connection adapter.ConnectionManager
	tags       []string
	index      atomic.Int64
	outbounds  []adapter.Outbound
	interval   time.Duration
	cancel     context.CancelFunc
}

func NewOutbound(ctx context.Context, router adapter.Router, logger log.ContextLogger, tag string, options pluginoption.BalancerOutboundOptions) (adapter.Outbound, error) {
	if len(options.Outbounds) == 0 {
		return nil, E.New("missing tags")
	}
	interval := options.Interval.Build()
	if interval <= 0 {
		interval = 5 * time.Second
	}
	ctx, cancel := context.WithCancel(ctx)
	return &Outbound{
		Adapter:    outbound.NewAdapter(pluginoption.TypeBalancer, tag, nil, options.Outbounds),
		ctx:        ctx,
		outbound:   service.FromContext[adapter.OutboundManager](ctx),
		connection: service.FromContext[adapter.ConnectionManager](ctx),
		tags:       options.Outbounds,
		interval:   interval,
		cancel:     cancel,
	}, nil
}

func (o *Outbound) Start(stage adapter.StartStage) error {
	if stage != adapter.StartStateStart {
		return nil
	}
	outbounds := make([]adapter.Outbound, len(o.tags))
	for i, tag := range o.tags {
		detour, loaded := o.outbound.Outbound(tag)
		if !loaded {
			return E.New("outbound ", i, " not found: ", tag)
		}
		outbounds[i] = detour
	}
	o.outbounds = outbounds
	go o.loopRotate()
	return nil
}

func (o *Outbound) loopRotate() {
	timer := time.NewTimer(o.interval)
	defer timer.Stop()
	for {
		select {
		case <-o.ctx.Done():
			return
		case <-timer.C:
			o.increaseIndex()
			timer.Reset(o.interval)
		}
	}
}

func (o *Outbound) increaseIndex() int {
	for {
		old := o.index.Load()
		newValue := old + 1
		if int(newValue) >= len(o.outbounds) {
			newValue = 0
		}
		if o.index.CompareAndSwap(old, newValue) {
			return int(newValue)
		}
	}
}

func (o *Outbound) selected() adapter.Outbound {
	return o.outbounds[o.index.Load()]
}

func (o *Outbound) Close() error {
	o.cancel()
	return nil
}

func (o *Outbound) Network() []string {
	return o.selected().Network()
}

func (o *Outbound) Now() string {
	return o.selected().Tag()
}

func (o *Outbound) All() []string {
	return o.tags
}

func (o *Outbound) References() []string {
	return []string{o.Now()}
}

func (o *Outbound) DialContext(ctx context.Context, network string, destination M.Socksaddr) (net.Conn, error) {
	return o.selected().DialContext(ctx, network, destination)
}

func (o *Outbound) ListenPacket(ctx context.Context, destination M.Socksaddr) (net.PacketConn, error) {
	return o.selected().ListenPacket(ctx, destination)
}

func (o *Outbound) NewConnection(ctx context.Context, conn net.Conn, metadata adapter.InboundContext, onClose N.CloseHandlerFunc) {
	selected := o.selected()
	if outboundHandler, isHandler := selected.(adapter.ConnectionHandler); isHandler {
		outboundHandler.NewConnection(ctx, conn, metadata, onClose)
	} else {
		o.connection.NewConnection(ctx, o, conn, metadata, onClose)
	}
}

func (o *Outbound) NewPacketConnection(ctx context.Context, conn N.PacketConn, metadata adapter.InboundContext, onClose N.CloseHandlerFunc) {
	selected := o.selected()
	if outboundHandler, isHandler := selected.(adapter.PacketConnectionHandler); isHandler {
		outboundHandler.NewPacketConnection(ctx, conn, metadata, onClose)
	} else {
		o.connection.NewPacketConnection(ctx, o, conn, metadata, onClose)
	}
}
