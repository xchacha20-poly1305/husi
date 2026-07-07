package trusttunnel

import (
	"bytes"
	"context"
	"net"
	"net/netip"
	"slices"
	"sync"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing-tun"
	"github.com/sagernet/sing-tun/gtcpip/header"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/buf"
	"github.com/sagernet/sing/common/cache"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"

	"github.com/xchacha20-poly1305/sing-trusttunnel"
)

const (
	defaultICMPRequestTimeout = time.Minute
	defaultICMPRequestLimit   = 128
)

var _ tun.Port = (*icmpPort)(nil)

type icmpPort struct {
	ctx     context.Context
	logger  logger.ContextLogger
	client  *trusttunnel.Client
	timeout time.Duration

	returnAccess sync.RWMutex
	returnPaths  []tun.Return

	connAccess   sync.Mutex
	conn         *trusttunnel.IcmpConn
	requests     *cache.LruCache[icmpRequestKey, icmpRequestData]
	readLoopDone chan struct{}
	closed       atomic.Bool
}

func newICMPPort(ctx context.Context, logger logger.ContextLogger, client *trusttunnel.Client, timeout time.Duration) *icmpPort {
	if timeout <= 0 {
		timeout = defaultICMPRequestTimeout
	}
	return &icmpPort{
		ctx:     ctx,
		logger:  logger,
		client:  client,
		timeout: timeout,
		requests: cache.New[icmpRequestKey, icmpRequestData](
			cache.WithAge[icmpRequestKey, icmpRequestData](int64(timeout.Seconds())),
			cache.WithSize[icmpRequestKey, icmpRequestData](defaultICMPRequestLimit),
		),
		readLoopDone: make(chan struct{}),
	}
}

func (p *icmpPort) PortAddresses() (v4 netip.Addr, v6 netip.Addr) {
	return netip.IPv4Unspecified(), netip.IPv6Unspecified()
}

func (p *icmpPort) PortMTU() uint32 {
	return 0
}

func (p *icmpPort) AttachReturn(returnPath tun.Return) error {
	if p.closed.Load() {
		return net.ErrClosed
	}
	p.returnAccess.Lock()
	defer p.returnAccess.Unlock()
	if slices.Contains(p.returnPaths, returnPath) {
		return nil
	}
	p.returnPaths = append(slices.Clip(p.returnPaths), returnPath)
	return nil
}

func (p *icmpPort) DetachReturn(returnPath tun.Return) error {
	p.returnAccess.Lock()
	defer p.returnAccess.Unlock()
	// Do not use slices.DeleteFunc, which modifies the slice.
	returnPaths := make([]tun.Return, 0, len(p.returnPaths))
	for _, existing := range p.returnPaths {
		if existing != returnPath {
			returnPaths = append(returnPaths, existing)
		}
	}
	p.returnPaths = returnPaths
	return nil
}

func (p *icmpPort) WritePackets(packets [][]byte) error {
	if p.closed.Load() {
		return net.ErrClosed
	}
	var errs []error
	for _, packet := range packets {
		err := p.writePacket(packet)
		if err != nil {
			errs = append(errs, err)
		}
	}
	return E.Errors(errs...)
}

func (p *icmpPort) writePacket(packet []byte) error {
	request, handled := parseICMPEchoRequest(packet)
	if !handled {
		return nil
	}
	conn, err := p.offerConn()
	if err != nil {
		return E.Cause(err, "get ICMP connection")
	}
	p.registerRequest(request.isIPv6, request.destination, request.identifier, request.sequence, request.source, request.ttl, request.payload)
	return conn.WritePing(request.identifier, request.destination, request.sequence, request.ttl, uint16(len(request.payload)))
}

func (p *icmpPort) offerConn() (*trusttunnel.IcmpConn, error) {
	p.connAccess.Lock()
	defer p.connAccess.Unlock()
	if p.readLoopDone != nil {
		select {
		case <-p.readLoopDone:
		default:
			if p.conn != nil {
				return p.conn, nil
			}
		}
	}
	conn, err := p.client.ListenICMP(p.ctx)
	if err != nil {
		return nil, err
	}
	p.conn = conn
	p.readLoopDone = make(chan struct{})
	go p.loopRead(conn, p.readLoopDone)
	p.logger.InfoContext(p.ctx, "created shared ICMP connection")
	return conn, nil
}

func (p *icmpPort) registerRequest(isIPv6 bool, destination netip.Addr, id uint16, seq uint16, source netip.Addr, ttl uint8, payload []byte) {
	key := icmpRequestKey{replySource: destination, id: id, seq: seq, isIPv6: isIPv6}
	p.requests.Store(key, icmpRequestData{source: source, ttl: ttl, payload: payload})
}

func (p *icmpPort) popRequest(isIPv6 bool, replySource netip.Addr, id uint16, seq uint16) (icmpRequestData, bool) {
	key := icmpRequestKey{replySource: replySource, id: id, seq: seq, isIPv6: isIPv6}
	data, loaded := p.requests.Load(key)
	if loaded {
		p.requests.Delete(key)
		return data, true
	}
	return common.DefaultValue[icmpRequestData](), false
}

func (p *icmpPort) loopRead(conn *trusttunnel.IcmpConn, done chan struct{}) {
	defer close(done)
	for {
		id, source, icmpType, code, sequence, err := conn.ReadPing()
		if err != nil {
			if E.IsClosed(err) {
				p.logger.DebugContext(p.ctx, "ICMP connection closed")
			} else {
				p.logger.ErrorContext(p.ctx, E.Cause(err, "receive ICMP echo reply"))
			}
			return
		}
		if !source.IsValid() {
			continue
		}
		if source.Is6() {
			if header.ICMPv6Type(icmpType) != header.ICMPv6EchoReply {
				continue
			}
			requestData, loaded := p.popRequest(true, source, id, sequence)
			if !loaded {
				continue
			}
			packet := buildICMPv6Reply(source, requestData.source, header.ICMPv6Type(icmpType), header.ICMPv6Code(code), id, sequence, requestData.ttl, requestData.payload)
			if packet == nil {
				continue
			}
			err = p.writeReturnPacket(packet.Bytes())
			packet.Release()
			if err != nil {
				p.logger.ErrorContext(p.ctx, E.Cause(err, "write ICMPv6 echo reply"))
			}
		} else {
			if header.ICMPv4Type(icmpType) != header.ICMPv4EchoReply {
				continue
			}
			data, loaded := p.popRequest(false, source, id, sequence)
			if !loaded {
				continue
			}
			packet := buildICMPv4Reply(source, data.source, header.ICMPv4Type(icmpType), header.ICMPv4Code(code), id, sequence, data.ttl, data.payload)
			if packet == nil {
				continue
			}
			err = p.writeReturnPacket(packet.Bytes())
			packet.Release()
			if err != nil {
				p.logger.ErrorContext(p.ctx, E.Cause(err, "write ICMPv4 echo reply"))
			}
		}
	}
}

func (p *icmpPort) writeReturnPacket(packet []byte) error {
	p.returnAccess.RLock()
	returnPaths := p.returnPaths
	p.returnAccess.RUnlock()

	if len(returnPaths) == 0 {
		return E.New("no return path attached")
	}

	for _, returnPath := range returnPaths {
		headroom := returnPath.ReturnHeadroom()
		buffer := make([]byte, headroom+len(packet))
		copy(buffer[headroom:], packet)
		unconsumed := returnPath.ReturnPackets([][]byte{buffer})
		if len(unconsumed) == 0 {
			return nil
		}
	}
	return E.New("all return paths rejected packet")
}

func (p *icmpPort) Close() error {
	if p.closed.Swap(true) {
		return nil
	}
	p.connAccess.Lock()
	conn := p.conn
	p.conn = nil
	p.readLoopDone = nil
	p.connAccess.Unlock()

	return common.Close(
		common.PtrOrNil(conn),
	)
}

type icmpEchoRequest struct {
	isIPv6      bool
	source      netip.Addr
	destination netip.Addr
	identifier  uint16
	sequence    uint16
	ttl         uint8
	payload     []byte
}

func parseICMPEchoRequest(packet []byte) (icmpEchoRequest, bool) {
	switch header.IPVersion(packet) {
	case header.IPv4Version:
		ipHdr := header.IPv4(packet)
		if !ipHdr.IsValid(len(packet)) || ipHdr.TransportProtocol() != header.ICMPv4ProtocolNumber || ipHdr.PayloadLength() < header.ICMPv4MinimumSize {
			return common.DefaultValue[icmpEchoRequest](), false
		}
		if !ipHdr.IsChecksumValid() {
			return common.DefaultValue[icmpEchoRequest](), false
		}
		icmpHdr := header.ICMPv4(ipHdr.Payload())
		if icmpHdr.Type() != header.ICMPv4Echo || icmpHdr.Code() != 0 {
			return common.DefaultValue[icmpEchoRequest](), false
		}
		return icmpEchoRequest{
			isIPv6:      false,
			source:      ipHdr.SourceAddr(),
			destination: ipHdr.DestinationAddr(),
			identifier:  icmpHdr.Ident(),
			sequence:    icmpHdr.Sequence(),
			ttl:         ipHdr.TTL(),
			payload:     bytes.Clone(icmpHdr.Payload()),
		}, true
	case header.IPv6Version:
		ipHdr := header.IPv6(packet)
		if !ipHdr.IsValid(len(packet)) || ipHdr.TransportProtocol() != header.ICMPv6ProtocolNumber || ipHdr.PayloadLength() < header.ICMPv6MinimumSize {
			return common.DefaultValue[icmpEchoRequest](), false
		}
		icmpHdr := header.ICMPv6(ipHdr.Payload())
		if icmpHdr.Type() != header.ICMPv6EchoRequest || icmpHdr.Code() != 0 {
			return common.DefaultValue[icmpEchoRequest](), false
		}
		return icmpEchoRequest{
			isIPv6:      true,
			source:      ipHdr.SourceAddr(),
			destination: ipHdr.DestinationAddr(),
			identifier:  icmpHdr.Ident(),
			sequence:    icmpHdr.Sequence(),
			ttl:         ipHdr.HopLimit(),
			payload:     bytes.Clone(icmpHdr.Payload()),
		}, true
	default:
		return common.DefaultValue[icmpEchoRequest](), false
	}
}

type icmpRequestKey struct {
	replySource netip.Addr
	id          uint16
	seq         uint16
	isIPv6      bool
}

type icmpRequestData struct {
	source  netip.Addr
	ttl     uint8
	payload []byte
}

func buildICMPv4Reply(src, dst netip.Addr, icmpType header.ICMPv4Type, code header.ICMPv4Code, id uint16, seq uint16, ttl uint8, payload []byte) *buf.Buffer {
	if !src.Is4() || !dst.Is4() {
		return nil
	}
	icmpLen := header.ICMPv4MinimumSize + len(payload)
	totalLen := header.IPv4MinimumSize + icmpLen
	packet := buf.NewSize(totalLen)
	data := packet.Extend(totalLen)
	ipHdr := header.IPv4(data)
	ipHdr.Encode(&header.IPv4Fields{
		TotalLength: uint16(totalLen),
		TTL:         ttl,
		Protocol:    uint8(header.ICMPv4ProtocolNumber),
		SrcAddr:     src,
		DstAddr:     dst,
	})
	ipHdr.SetChecksum(^ipHdr.CalculateChecksum())
	icmpHdr := header.ICMPv4(data[header.IPv4MinimumSize:])
	icmpHdr.SetType(icmpType)
	icmpHdr.SetCode(code)
	icmpHdr.SetIdent(id)
	icmpHdr.SetSequence(seq)
	copy(icmpHdr.Payload(), payload)
	icmpHdr.SetChecksum(0)
	icmpHdr.SetChecksum(header.ICMPv4Checksum(icmpHdr, 0))
	return packet
}

func buildICMPv6Reply(src, dst netip.Addr, icmpType header.ICMPv6Type, code header.ICMPv6Code, id uint16, seq uint16, hopLimit uint8, payload []byte) *buf.Buffer {
	if !src.Is6() || !dst.Is6() {
		return nil
	}
	icmpLen := header.ICMPv6MinimumSize + len(payload)
	totalLen := header.IPv6MinimumSize + icmpLen
	packet := buf.NewSize(totalLen)
	data := packet.Extend(totalLen)
	ipHdr := header.IPv6(data)
	ipHdr.Encode(&header.IPv6Fields{
		PayloadLength:     uint16(icmpLen),
		TransportProtocol: header.ICMPv6ProtocolNumber,
		HopLimit:          hopLimit,
		SrcAddr:           src,
		DstAddr:           dst,
	})
	icmpHdr := header.ICMPv6(data[header.IPv6MinimumSize:])
	icmpHdr.SetType(icmpType)
	icmpHdr.SetCode(code)
	icmpHdr.SetIdent(id)
	icmpHdr.SetSequence(seq)
	copy(icmpHdr.Payload(), payload)
	icmpHdr.SetChecksum(0)
	icmpHdr.SetChecksum(header.ICMPv6Checksum(header.ICMPv6ChecksumParams{
		Header: icmpHdr,
		Src:    ipHdr.SourceAddressSlice(),
		Dst:    ipHdr.DestinationAddressSlice(),
	}))
	return packet
}
