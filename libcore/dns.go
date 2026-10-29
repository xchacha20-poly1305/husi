//go:build android

package libcore

import (
	"context"
	"net/netip"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing-box/dns/transport/local"
	"github.com/sagernet/sing-box/dns/transport/mdns"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"

	mDNS "github.com/miekg/dns"
)

var (
	_ adapter.DNSTransport                    = (*platformTransport)(nil)
	_ adapter.DNSTransportWithPreferredDomain = (*platformTransport)(nil)
	_ adapter.DNSTransportWithConfiguration   = (*platformTransport)(nil)
	_ adapter.DNSTransportWithEnvironment     = (*platformTransport)(nil)
)

type platformTransport struct {
	dns.TransportAdapter
	iif               LocalDNSTransport
	preferredResolver *local.PreferredDomainResolver
	networkManager    adapter.NetworkManager
}

func (p *platformTransport) Start(stage adapter.StartStage, scope *adapter.Scope) error {
	p.preferredResolver.Start(stage)
	return nil
}

func newPlatformTransport(ctx context.Context, logger log.ContextLogger, iif LocalDNSTransport, tag string, options option.LocalDNSServerOptions) (adapter.DNSTransport, error) {
	preferredResolver, err := local.NewPreferredDomainResolver(ctx, logger, options)
	if err != nil {
		return nil, err
	}
	transport := &platformTransport{
		TransportAdapter:  dns.NewTransportAdapterWithLocalOptions(C.DNSTypeLocal, tag, options),
		iif:               iif,
		preferredResolver: preferredResolver,
		networkManager:    service.FromContext[adapter.NetworkManager](ctx),
	}
	return transport, nil
}

func registerPlatformLocalDNSTransport(registry *dns.TransportRegistry, platformInterface PlatformInterface) {
	if platformInterface == nil {
		return
	}
	localTransport := platformInterface.LocalDNSTransport()
	if localTransport == nil {
		return
	}
	dns.RegisterTransport[option.LocalDNSServerOptions](registry, C.DNSTypeLocal, func(ctx context.Context, logger log.ContextLogger, tag string, options option.LocalDNSServerOptions) (adapter.DNSTransport, error) {
		return newPlatformTransport(ctx, logger, localTransport, tag, options)
	})
}

func (p *platformTransport) Reset() {
}

func (p *platformTransport) PreferredDomain(domain string) bool {
	if !p.iif.Raw() && mdns.IsLocalDomain(domain) {
		return false
	}
	return p.preferredResolver.PreferredDomain(domain)
}

func (p *platformTransport) ServerAddresses() []netip.Addr {
	defaultInterface := p.defaultNetworkInterface()
	if defaultInterface == nil {
		return nil
	}
	var serverAddresses []netip.Addr
	for _, server := range defaultInterface.DNSServers {
		serverAddress, err := netip.ParseAddr(server)
		if err == nil {
			serverAddresses = append(serverAddresses, serverAddress)
		}
	}
	return serverAddresses
}

func (p *platformTransport) SearchDomains() []string {
	defaultInterface := p.defaultNetworkInterface()
	if defaultInterface == nil {
		return nil
	}
	return defaultInterface.DNSSearchDomains
}

func (p *platformTransport) Environment() []string {
	defaultInterface := p.defaultNetworkInterface()
	if defaultInterface == nil {
		return nil
	}
	return defaultInterface.DNSServers
}

func (p *platformTransport) defaultNetworkInterface() *adapter.NetworkInterface {
	if p.networkManager == nil {
		return nil
	}
	return p.networkManager.DefaultNetworkInterface()
}

func (p *platformTransport) Exchange(ctx context.Context, message *mDNS.Msg) (*mDNS.Msg, error) {
	localResponse := p.preferredResolver.Lookup(message)
	if localResponse != nil {
		return localResponse, nil
	}
	response := &ExchangeContext{
		context: ctx,
	}
	if p.iif.Raw() {
		return exchangePlatformDNS(ctx, p.iif, message)
	}

	question := message.Question[0]
	var network string
	switch question.Qtype {
	case mDNS.TypeA:
		network = "ip4"
	case mDNS.TypeAAAA:
		network = "ip6"
	default:
		return nil, E.New("only IP queries are supported by current version of Android")
	}
	done := make(chan error, 1)
	go func() {
		lookupErr := p.iif.Lookup(response, network, question.Name)
		if lookupErr == nil {
			lookupErr = response.error
		}
		done <- lookupErr
	}()
	select {
	case err := <-done:
		if err != nil {
			return nil, err
		}
		return dns.FixedResponse(message.Id, question, response.addresses, C.DefaultDNSTTL), nil
	case <-ctx.Done():
		return nil, ctx.Err()
	}
}

func (p *platformTransport) ExchangeAsync(ctx context.Context, message *mDNS.Msg, callback func(response *mDNS.Msg, err error)) {
	go func() {
		callback(p.Exchange(ctx, message))
	}()
}
