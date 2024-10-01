// Package anchor provides anchor API server.
package anchor

import (
	"context"
	"net"
	"os"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/buf"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"
	N "github.com/sagernet/sing/common/network"

	"github.com/xchacha20-poly1305/anchor"
)

var (
	_ adapter.Service = (*Anchor)(nil)
	_ E.Handler       = (*Anchor)(nil)
)

type Anchor struct {
	ctx        context.Context
	logger     logger.ContextLogger
	packetConn net.PacketConn
	response   []byte
	done       chan struct{}
}

func New(ctx context.Context, ctxLogger logger.ContextLogger, options Options) (*Anchor, error) {
	packetConn, err := net.ListenUDP(N.NetworkUDP+"4", &net.UDPAddr{
		IP:   net.IPv4zero, // Not listen IPv6
		Port: anchor.Port,
	})
	if err != nil {
		return nil, E.Cause(err, "listen anchor API")
	}
	if ctxLogger == nil {
		ctxLogger = logger.NOP()
	}
	response, _ := (&anchor.Response{
		Version:    anchor.Version,
		DnsPort:    options.DnsPort,
		DeviceName: options.DeviceName,
		SocksPort:  options.SocksPort,
		User:       options.User,
	}).MarshalBinary()
	return &Anchor{
		ctx:        ctx,
		logger:     ctxLogger,
		response:   response,
		packetConn: packetConn,
		done:       make(chan struct{}),
	}, nil
}

func (a *Anchor) Start() error {
	go a.loop()
	return nil
}

func (a *Anchor) loop() {
	a.logger.InfoContext(a.ctx, "Anchor: started loop")
	stop := context.AfterFunc(a.ctx, func() {
		_ = a.Close()
	})
	for {
		select {
		case <-a.ctx.Done():
			return
		case <-a.done:
			return
		default:
		}
		buffer := buf.NewSize(anchor.MaxQuerySize)
		_, source, err := buffer.ReadPacketFrom(a.packetConn)
		if err != nil {
			stop()
			buffer.Release()
			a.NewError(a.ctx, err)
			return
		}
		go a.handle(source, buffer)
	}
}

func (a *Anchor) handle(source net.Addr, buffer *buf.Buffer) {
	defer buffer.Release()
	a.logger.TraceContext(a.ctx, "Anchor: new query from: ", source)
	query, err := anchor.ParseQuery(buffer.Bytes())
	if err != nil {
		a.NewError(a.ctx, err)
		return
	}
	a.logger.TraceContext(a.ctx, "Anchor: device name: ", query.DeviceName)
	_, err = a.packetConn.WriteTo(a.response, source)
	if err != nil {
		a.NewError(a.ctx, err)
		return
	}
}

func (a *Anchor) NewError(ctx context.Context, err error) {
	if E.IsClosedOrCanceled(err) {
		return
	}
	a.logger.InfoContext(ctx, "Anchor: ", err)
}

func (a *Anchor) Close() error {
	select {
	case <-a.done:
		return os.ErrClosed
	default:
		close(a.done)
	}
	return common.Close(a.packetConn)
}
