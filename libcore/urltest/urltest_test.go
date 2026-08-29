package urltest_test

import (
	"context"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/urltest"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func TestFlagsFromProto(t *testing.T) {
	assert.Zero(t, urltest.FlagsFromProto(nil), "no options means no flags")
	assert.Equal(t, urltest.UnifiedDelay|urltest.IgnoreHandshakeTime, urltest.FlagsFromProto(&husiv1.URLTestOptions{
		UnifiedDelay:        true,
		IgnoreHandshakeTime: true,
	}))
}

func TestWrapError(t *testing.T) {
	require.NoError(t, urltest.WrapError(nil))
	for _, testCase := range []struct {
		name string
		err  error
		code codes.Code
	}{
		{"not found", E.Cause(urltest.ErrOutboundNotFound, "missing"), codes.NotFound},
		{"canceled", context.DeadlineExceeded, codes.DeadlineExceeded},
		{"other", E.New("boom"), codes.Internal},
	} {
		t.Run(testCase.name, func(t *testing.T) {
			st, ok := status.FromError(urltest.WrapError(testCase.err))
			require.True(t, ok, "expected grpc status")
			assert.Equal(t, testCase.code, st.Code())
		})
	}
}

func TestMeasure(t *testing.T) {
	var requests atomic.Int32
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		requests.Add(1)
		w.WriteHeader(http.StatusNoContent)
	}))
	t.Cleanup(server.Close)

	var dialer N.Dialer = new(N.DefaultDialer)
	latency, err := urltest.Measure(t.Context(), server.URL, dialer, 0)
	require.NoError(t, err)
	assert.GreaterOrEqual(t, latency, uint16(0))
	assert.Equal(t, int32(1), requests.Load())

	// A unified delay measures the second request, so the connection setup of
	// the first one is not part of the result.
	requests.Store(0)
	_, err = urltest.Measure(t.Context(), server.URL, dialer, urltest.UnifiedDelay)
	require.NoError(t, err)
	assert.Equal(t, int32(2), requests.Load())
}

func TestMeasureUnreachable(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(http.ResponseWriter, *http.Request) {}))
	url := server.URL
	server.Close()

	var dialer N.Dialer = new(N.DefaultDialer)
	_, err := urltest.Measure(t.Context(), url, dialer, 0)
	require.Error(t, err, "expected a dial failure against a closed server")
}
