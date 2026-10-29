package ipprobe

import (
	"context"
	"net"
	"net/netip"
	"testing"
	"testing/synctest"
	"time"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"

	mDNS "github.com/miekg/dns"
	"github.com/stretchr/testify/require"
)

type testResolver struct {
	exchange func(context.Context, *mDNS.Msg) (*mDNS.Msg, error)
	lookup   func(context.Context, string) ([]netip.Addr, error)
}

func (r testResolver) Exchange(ctx context.Context, message *mDNS.Msg, _ adapter.DNSQueryOptions) (*mDNS.Msg, error) {
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
				exchange: func(ctx context.Context, question *mDNS.Msg) (*mDNS.Msg, error) {
					deadline, hasDeadline := ctx.Deadline()
					require.True(t, hasDeadline)
					require.InDelta(t, C.DNSTimeout.Seconds(), time.Until(deadline).Seconds(), 1)
					require.Equal(t, "_"+scheme+"._"+server.transport+".example.org.", question.Question[0].Name)
					return &mDNS.Msg{Answer: []mDNS.RR{&mDNS.SRV{Target: "target.example.org.", Port: 4567}}}, nil
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
		response  *mDNS.Msg
		wantError string
	}{
		{"empty", &mDNS.Msg{}, ""},
		{"nxdomain", &mDNS.Msg{Rcode: mDNS.RcodeNameError}, ""},
		{"servfail", &mDNS.Msg{Rcode: mDNS.RcodeServerFailure}, "SERVFAIL"},
		{"unavailable", &mDNS.Msg{Answer: []mDNS.RR{&mDNS.SRV{Target: "."}}}, "service unavailable"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			server, err := parseStunServer("stun:example.org:8000")
			require.NoError(t, err)
			resolver := testResolver{
				exchange: func(context.Context, *mDNS.Msg) (*mDNS.Msg, error) { return tc.response, nil },
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
	records := []*mDNS.SRV{
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
		resolver := testResolver{exchange: func(ctx context.Context, _ *mDNS.Msg) (*mDNS.Msg, error) {
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
	resolver := testResolver{exchange: func(ctx context.Context, _ *mDNS.Msg) (*mDNS.Msg, error) {
		cancel()
		<-ctx.Done()
		return nil, ctx.Err()
	}}
	_, err = discoverServers(ctx, resolver, server)
	require.ErrorIs(t, err, context.Canceled)
}

func TestSystemDiscovery(t *testing.T) {
	for _, test := range []struct {
		name      string
		rcode     int
		srv       bool
		wantPort  uint16
		wantError string
	}{
		{name: "SRV", srv: true, wantPort: 4567},
		{name: "no SRV", wantPort: 8000},
		{name: "NXDOMAIN", rcode: mDNS.RcodeNameError, wantPort: 8000},
		{name: "failure", rcode: mDNS.RcodeServerFailure, wantError: "lookup STUN SRV"},
	} {
		t.Run(test.name, func(t *testing.T) {
			listener, err := net.ListenPacket("udp", "127.0.0.1:0")
			require.NoError(t, err)
			server := &mDNS.Server{PacketConn: listener, Handler: mDNS.HandlerFunc(func(writer mDNS.ResponseWriter, request *mDNS.Msg) {
				response := new(mDNS.Msg)
				response.SetReply(request)
				response.Authoritative = true
				question := request.Question[0]
				switch question.Qtype {
				case mDNS.TypeSRV:
					if question.Name != "_stun._udp.example.org." {
						response.Rcode = mDNS.RcodeNameError
						break
					}
					response.Rcode = test.rcode
					if test.srv {
						response.Answer = []mDNS.RR{
							&mDNS.SRV{
								Hdr: mDNS.RR_Header{
									Name:   question.Name,
									Rrtype: mDNS.TypeSRV,
									Class:  mDNS.ClassINET,
								},
								Target: "target.example.org.",
								Port:   4567,
							},
						}
					}
				case mDNS.TypeA:
					host := "example.org."
					if test.srv {
						host = "target.example.org."
					}
					if question.Name == host {
						response.Answer = []mDNS.RR{
							&mDNS.A{
								Hdr: mDNS.RR_Header{
									Name:   question.Name,
									Rrtype: mDNS.TypeA,
									Class:  mDNS.ClassINET,
								},
								A: net.IPv4(192, 0, 2, 1),
							},
						}
					}
				}
				_ = writer.WriteMsg(response)
			})}
			ready := make(chan struct{})
			server.NotifyStartedFunc = func() { close(ready) }
			completed := make(chan error, 1)
			go func() {
				completed <- server.ActivateAndServe()
			}()
			<-ready
			t.Cleanup(func() {
				require.NoError(t, server.Shutdown())
				require.NoError(t, <-completed)
			})
			original := net.DefaultResolver
			net.DefaultResolver = &net.Resolver{PreferGo: true, Dial: func(ctx context.Context, network, _ string) (net.Conn, error) {
				return (&net.Dialer{}).DialContext(ctx, network, listener.LocalAddr().String())
			}}
			t.Cleanup(func() {
				net.DefaultResolver = original
			})
			stun, err := parseStunServer("stun:example.org.:8000")
			require.NoError(t, err)
			addresses, err := discoverServers(t.Context(), nil, stun)
			if test.wantError != "" {
				require.ErrorContains(t, err, test.wantError)
				return
			}
			require.NoError(t, err)
			require.Len(t, addresses, 1)
			require.Equal(t, "192.0.2.1", addresses[0].Addr.String())
			require.Equal(t, test.wantPort, addresses[0].Port)
		})
	}
}
