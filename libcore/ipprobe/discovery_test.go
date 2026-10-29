package ipprobe

import (
	"context"
	"net/netip"
	"testing"
	"testing/synctest"
	"time"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"

	"github.com/miekg/dns"
	"github.com/stretchr/testify/require"
)

type testResolver struct {
	exchange func(context.Context, *dns.Msg) (*dns.Msg, error)
	lookup   func(context.Context, string) ([]netip.Addr, error)
}

func (r testResolver) Exchange(ctx context.Context, message *dns.Msg, _ adapter.DNSQueryOptions) (*dns.Msg, error) {
	return r.exchange(ctx, message)
}

func (r testResolver) Lookup(ctx context.Context, name string, _ adapter.DNSQueryOptions) ([]netip.Addr, error) {
	return r.lookup(ctx, name)
}

func TestDiscovery(t *testing.T) {
	for _, scheme := range []string{"stun", "stuns", "turn", "turns"} {
		t.Run(scheme, func(t *testing.T) {
			server, err := parseStunServer(scheme + ":example.org")
			require.NoError(t, err)
			resolver := testResolver{
				exchange: func(ctx context.Context, question *dns.Msg) (*dns.Msg, error) {
					deadline, hasDeadline := ctx.Deadline()
					require.True(t, hasDeadline)
					require.InDelta(t, C.DNSTimeout.Seconds(), time.Until(deadline).Seconds(), 1)
					require.Equal(t, "_"+scheme+"._"+server.transport+".example.org.", question.Question[0].Name)
					return &dns.Msg{Answer: []dns.RR{&dns.SRV{Target: "target.example.org.", Port: 4567}}}, nil
				},
				lookup: func(_ context.Context, name string) ([]netip.Addr, error) {
					require.Equal(t, "target.example.org", name)
					return []netip.Addr{netip.MustParseAddr("192.0.2.1"), netip.MustParseAddr("2001:db8::1")}, nil
				},
			}
			addresses, err := discoverServers(t.Context(), resolver, server)
			require.NoError(t, err)
			require.Len(t, addresses, 2)
			require.EqualValues(t, 4567, addresses[0].Port)
			require.Equal(t, "2001:db8::1", addresses[1].Addr.String())
		})
	}
}

func TestDiscoveryFallbackAndFailures(t *testing.T) {
	for _, tc := range []struct {
		name      string
		response  *dns.Msg
		wantError string
	}{
		{"empty", &dns.Msg{}, ""},
		{"nxdomain", &dns.Msg{Rcode: dns.RcodeNameError}, ""},
		{"servfail", &dns.Msg{Rcode: dns.RcodeServerFailure}, "SERVFAIL"},
		{"unavailable", &dns.Msg{Answer: []dns.RR{&dns.SRV{Target: "."}}}, "service unavailable"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server, err := parseStunServer("stun:example.org:8000")
			require.NoError(t, err)
			resolver := testResolver{
				exchange: func(context.Context, *dns.Msg) (*dns.Msg, error) { return tc.response, nil },
				lookup: func(_ context.Context, host string) ([]netip.Addr, error) {
					require.Empty(t, tc.wantError)
					require.Equal(t, "example.org", host)
					return []netip.Addr{netip.MustParseAddr("192.0.2.1")}, nil
				},
			}
			addresses, err := discoverServers(t.Context(), resolver, server)
			if tc.wantError != "" {
				require.ErrorContains(t, err, tc.wantError)
				return
			}
			require.NoError(t, err)
			require.EqualValues(t, 8000, addresses[0].Port)
		})
	}
}

func TestSRVPriority(t *testing.T) {
	records := []*dns.SRV{
		{Priority: 20, Weight: 100, Target: "last"},
		{Priority: 10, Target: "zero"},
		{Priority: 10, Weight: 10, Target: "weighted"},
	}
	for range 100 {
		ordered := orderSRV(records)
		require.Equal(t, "last", ordered[2].Target)
		require.ElementsMatch(t, records, ordered)
	}
}

func TestDNSDeadlineIndependent(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		server, err := parseStunServer("stun:example.org")
		require.NoError(t, err)
		resolver := testResolver{exchange: func(ctx context.Context, _ *dns.Msg) (*dns.Msg, error) {
			<-ctx.Done()
			return nil, ctx.Err()
		}}
		_, err = discoverServers(t.Context(), resolver, server)
		require.ErrorIs(t, err, context.DeadlineExceeded)
		require.NoError(t, t.Context().Err())
	})
}

func TestDiscoveryCancellation(t *testing.T) {
	ctx, cancel := context.WithCancel(t.Context())
	server, err := parseStunServer("stun:example.org")
	require.NoError(t, err)
	resolver := testResolver{exchange: func(ctx context.Context, _ *dns.Msg) (*dns.Msg, error) {
		cancel()
		<-ctx.Done()
		return nil, ctx.Err()
	}}
	_, err = discoverServers(ctx, resolver, server)
	require.ErrorIs(t, err, context.Canceled)
}
