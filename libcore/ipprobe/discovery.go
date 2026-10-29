package ipprobe

import (
	"cmp"
	"context"
	"math/rand/v2"
	"net/netip"
	"slices"
	"strings"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"

	mDNS "github.com/miekg/dns"
)

type Resolver interface {
	Exchange(ctx context.Context, message *mDNS.Msg, options adapter.DNSQueryOptions) (*mDNS.Msg, error)
	Lookup(ctx context.Context, domain string, options adapter.DNSQueryOptions) ([]netip.Addr, error)
}

func discoverServers(ctx context.Context, resolver Resolver, server stunServer) ([]M.Socksaddr, error) {
	if server.address.IsIP() {
		return []M.Socksaddr{server.address}, nil
	}
	if resolver == nil {
		return nil, E.New("require DNS router")
	}
	ctx, cancel := context.WithTimeout(ctx, C.DNSTimeout)
	defer cancel()
	question := new(mDNS.Msg)
	question.SetQuestion(mDNS.Fqdn("_"+server.scheme+"._"+server.transport+"."+server.address.Fqdn), mDNS.TypeSRV)
	response, err := resolver.Exchange(ctx, question, adapter.DNSQueryOptions{})
	if err != nil {
		return nil, E.Cause(err, "lookup STUN SRV")
	}
	if response == nil {
		return nil, E.New("empty STUN DNS response")
	}
	if response.Rcode != mDNS.RcodeSuccess && response.Rcode != mDNS.RcodeNameError {
		return nil, E.New("STUN SRV DNS response: ", mDNS.RcodeToString[response.Rcode])
	}
	records := common.FilterIsInstance(response.Answer, func(it mDNS.RR) (*mDNS.SRV, bool) {
		srv, isSRV := it.(*mDNS.SRV)
		return srv, isSRV
	})
	targets := []M.Socksaddr{server.address}
	if len(records) > 0 {
		targets = nil
		for _, record := range orderSRV(records) {
			if record.Target == "." {
				return nil, E.New("STUN service unavailable")
			}
			targets = append(targets, M.ParseSocksaddrHostPort(strings.TrimSuffix(record.Target, "."), record.Port))
		}
	}
	var addresses []M.Socksaddr
	var lookupErrors []error
	for _, target := range targets {
		ips, err := resolver.Lookup(ctx, target.Fqdn, adapter.DNSQueryOptions{})
		if err != nil {
			lookupErrors = append(lookupErrors, err)
			continue
		}
		for _, ip := range ips {
			addresses = append(addresses, M.Socksaddr{Addr: ip, Port: target.Port})
		}
	}
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	if len(addresses) == 0 {
		return nil, E.Cause(E.Errors(lookupErrors...), "no STUN server addresses")
	}
	return addresses, nil
}

// RFC 2782: select by priority, then weighted random selection without replacement.
func orderSRV(records []*mDNS.SRV) []*mDNS.SRV {
	records = slices.Clone(records)
	slices.SortStableFunc(records, func(a, b *mDNS.SRV) int {
		return cmp.Compare(a.Priority, b.Priority)
	})
	ordered := make([]*mDNS.SRV, 0, len(records))
	for len(records) > 0 {
		end := 1
		for end < len(records) && records[end].Priority == records[0].Priority {
			end++
		}
		group := records[:end]
		slices.SortStableFunc(group, func(a, b *mDNS.SRV) int {
			return cmp.Compare(a.Weight, b.Weight)
		})
		for len(group) > 0 {
			total := 0
			for _, record := range group {
				total += int(record.Weight)
			}
			choice := rand.IntN(total + 1)
			index := 0
			for ; index < len(group)-1; index++ {
				choice -= int(group[index].Weight)
				if choice <= 0 {
					break
				}
			}
			ordered = append(ordered, group[index])
			group = slices.Delete(group, index, index+1)
		}
		records = records[end:]
	}
	return ordered
}
