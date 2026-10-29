package libcore

import (
	"context"
	"errors"
	"net/netip"
	"strings"
	"syscall"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/dns"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

	mDNS "github.com/miekg/dns"
	"github.com/xchacha20-poly1305/husi/libcore/v2/ipprobe"
)

type LocalDNSTransport interface {
	Raw() bool
	Lookup(ctx *ExchangeContext, network string, domain string) error
	Exchange(ctx *ExchangeContext, message []byte) error
}

var _ ipprobe.Resolver = platformIPResolver{}

type Func interface {
	Invoke() error
}

type ExchangeContext struct {
	context   context.Context
	message   mDNS.Msg
	addresses []netip.Addr
	error     error
}

func (c *ExchangeContext) OnCancel(callback Func) {
	go func() {
		<-c.context.Done()
		_ = callback.Invoke()
	}()
}

func (c *ExchangeContext) Success(result string) {
	var addresses []netip.Addr
	for address := range strings.SplitSeq(result, "\n") {
		if address == "" {
			continue
		}
		addresses = append(addresses, M.ParseSocksaddrHostPort(address, 0).Unwrap().Addr)
	}
	c.addresses = addresses
}

func (c *ExchangeContext) RawSuccess(result []byte) {
	err := c.message.Unpack(result)
	if err != nil {
		c.error = E.Cause(err, "parse response")
	}
}

func (c *ExchangeContext) ErrorCode(code int32) {
	c.error = dns.RcodeError(code)
}

func (c *ExchangeContext) ErrnoCode(code int32) {
	c.error = syscall.Errno(code)
}

type platformIPResolver struct {
	transport LocalDNSTransport
}

func (r platformIPResolver) Exchange(ctx context.Context, message *mDNS.Msg, _ adapter.DNSQueryOptions) (*mDNS.Msg, error) {
	if !r.transport.Raw() {
		return new(mDNS.Msg).SetReply(message), nil
	}
	return exchangePlatformDNS(ctx, r.transport, message)
}

func exchangePlatformDNS(ctx context.Context, transport LocalDNSTransport, message *mDNS.Msg) (*mDNS.Msg, error) {
	messageBytes, err := message.Pack()
	if err != nil {
		return nil, err
	}
	response := &ExchangeContext{context: ctx}
	done := make(chan error, 1)
	go func() {
		exchangeErr := transport.Exchange(response, messageBytes)
		if exchangeErr == nil {
			exchangeErr = response.error
		}
		done <- exchangeErr
	}()
	select {
	case err = <-done:
		if rcode, isRcode := errors.AsType[dns.RcodeError](err); isRcode {
			return dns.FixedResponseStatus(message, int(rcode)), nil
		}
		if err != nil {
			return nil, err
		}
		return &response.message, nil
	case <-ctx.Done():
		return nil, ctx.Err()
	}
}

func (r platformIPResolver) Lookup(ctx context.Context, domain string, _ adapter.DNSQueryOptions) ([]netip.Addr, error) {
	response := &ExchangeContext{context: ctx}
	done := make(chan error, 1)
	go func() {
		lookupErr := r.transport.Lookup(response, N.NetworkIP, domain)
		if lookupErr == nil {
			lookupErr = response.error
		}
		done <- lookupErr
	}()
	select {
	case err := <-done:
		return response.addresses, err
	case <-ctx.Done():
		return nil, ctx.Err()
	}
}
