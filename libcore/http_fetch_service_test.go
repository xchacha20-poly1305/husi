package libcore

import (
	"bytes"
	"context"
	"net/http"
	"net/http/httptest"
	"strconv"
	"testing"

	C "github.com/sagernet/sing-box/constant"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

// recordingHTTPFetchStream clones what it receives, the way a real stream
// marshals it, because HTTPFetch reuses its chunk buffer after Send.
type recordingHTTPFetchStream struct {
	grpc.ServerStream
	ctx  context.Context
	sent []*husiv1.HTTPFetchResponse
}

func (s *recordingHTTPFetchStream) Context() context.Context { return s.ctx }

func (s *recordingHTTPFetchStream) Send(response *husiv1.HTTPFetchResponse) error {
	s.sent = append(s.sent, proto.Clone(response).(*husiv1.HTTPFetchResponse))
	return nil
}

func TestHTTPFetchStreamsHeadThenBody(t *testing.T) {
	const userInfoHeader = "Subscription-Userinfo"
	body := bytes.Repeat([]byte("husi"), httpFetchChunkSize) // several chunks
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, request *http.Request) {
		writer.Header().Set(userInfoHeader, request.UserAgent())
		writer.Header().Set("Content-Length", strconv.Itoa(len(body)))
		_, _ = writer.Write(body)
	}))
	defer server.Close()

	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)
	stream := &recordingHTTPFetchStream{ctx: t.Context()}
	err := service.HTTPFetch(&husiv1.HTTPFetchRequest{
		Url:     server.URL,
		Headers: map[string]string{"user-agent": "husi-test"},
	}, stream)
	require.NoError(t, err)

	require.NotEmpty(t, stream.sent)
	head := stream.sent[0].GetHead()
	require.NotNil(t, head, "the first message must be the head")
	assert.Equal(t, "husi-test", head.GetHeaders()[userInfoHeader])
	assert.EqualValues(t, len(body), head.GetContentLength())

	var received bytes.Buffer
	for _, message := range stream.sent[1:] {
		require.Nil(t, message.GetHead(), "only one head")
		received.Write(message.GetChunk())
	}
	assert.Greater(t, len(stream.sent), 2, "the body must be split into chunks")
	assert.Equal(t, body, received.Bytes())
}

func TestHTTPFetchFailsOnNon200(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		http.Error(writer, "gone", http.StatusGone)
	}))
	defer server.Close()

	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)
	stream := &recordingHTTPFetchStream{ctx: t.Context()}
	err := service.HTTPFetch(&husiv1.HTTPFetchRequest{Url: server.URL}, stream)

	require.Error(t, err)
	assert.Contains(t, err.Error(), "410")
	assert.Empty(t, stream.sent, "a failed request sends no head")
}

func TestHTTPFetchRejectsInvalidAgeIdentities(t *testing.T) {
	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)
	err := service.HTTPFetch(&husiv1.HTTPFetchRequest{
		Url:           "https://husi.invalid",
		AgeIdentities: "not an identity",
	}, &recordingHTTPFetchStream{ctx: t.Context()})

	st, ok := status.FromError(err)
	require.True(t, ok, "expected grpc status, got %v", err)
	assert.Equal(t, codes.InvalidArgument, st.Code())
}

func TestHTTPFetchRequestFromProtoDeadline(t *testing.T) {
	request, err := httpFetchRequestFromProto(&husiv1.HTTPFetchRequest{})
	require.NoError(t, err)
	assert.Equal(t, C.TCPTimeout, request.Timeout)

	request, err = httpFetchRequestFromProto(&husiv1.HTTPFetchRequest{NoOverallDeadline: true})
	require.NoError(t, err)
	assert.Zero(t, request.Timeout)
}
