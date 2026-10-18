package libcore

import (
	"bytes"
	"context"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/sagernet/sing/common/buf"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	xwebdav "golang.org/x/net/webdav"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

// replayingWebDAVUploadStream hands out requests in order, then io.EOF.
type replayingWebDAVUploadStream struct {
	grpc.ServerStream
	ctx      context.Context
	requests []*husiv1.WebDAVUploadRequest
	closed   bool
}

func (s *replayingWebDAVUploadStream) Context() context.Context { return s.ctx }

func (s *replayingWebDAVUploadStream) Recv() (*husiv1.WebDAVUploadRequest, error) {
	if len(s.requests) == 0 {
		return nil, io.EOF
	}
	request := s.requests[0]
	s.requests = s.requests[1:]
	return request, nil
}

func (s *replayingWebDAVUploadStream) SendAndClose(*husiv1.WebDAVUploadResponse) error {
	s.closed = true
	return nil
}

// recordingWebDAVDownloadStream clones what it receives, the way a real
// stream marshals it, because the sender may reuse its chunk buffer.
type recordingWebDAVDownloadStream struct {
	grpc.ServerStream
	ctx  context.Context
	sent []*husiv1.WebDAVDownloadResponse
}

func (s *recordingWebDAVDownloadStream) Context() context.Context { return s.ctx }

func (s *recordingWebDAVDownloadStream) Send(response *husiv1.WebDAVDownloadResponse) error {
	s.sent = append(s.sent, proto.Clone(response).(*husiv1.WebDAVDownloadResponse))
	return nil
}

func (s *recordingWebDAVDownloadStream) content() []byte {
	var content []byte
	for _, response := range s.sent {
		content = append(content, response.GetChunk()...)
	}
	return content
}

func uploadHead(server *husiv1.WebDAVServer, name string, size int) *husiv1.WebDAVUploadRequest {
	return &husiv1.WebDAVUploadRequest{
		Payload: &husiv1.WebDAVUploadRequest_Head{Head: &husiv1.WebDAVUploadHead{
			Server: server,
			Name:   name,
			Size:   int64(size),
		}},
	}
}

func uploadChunk(chunk []byte) *husiv1.WebDAVUploadRequest {
	return &husiv1.WebDAVUploadRequest{
		Payload: &husiv1.WebDAVUploadRequest_Chunk{Chunk: chunk},
	}
}

func TestWebDAVServiceRoundTrip(t *testing.T) {
	httpServer := httptest.NewServer(&xwebdav.Handler{
		FileSystem: xwebdav.NewMemFS(),
		LockSystem: xwebdav.NewMemLS(),
	})
	defer httpServer.Close()
	server := &husiv1.WebDAVServer{Url: httpServer.URL + "/husi"}
	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)

	content := bytes.Repeat([]byte("husi"), buf.BufferSize) // several chunks
	half := len(content) / 2
	uploadStream := &replayingWebDAVUploadStream{
		ctx: t.Context(),
		requests: []*husiv1.WebDAVUploadRequest{
			uploadHead(server, "backup.json", len(content)),
			uploadChunk(content[:half]),
			uploadChunk(content[half:]),
		},
	}
	require.NoError(t, service.WebDAVUpload(uploadStream))
	assert.True(t, uploadStream.closed)

	list, err := service.WebDAVList(t.Context(), &husiv1.WebDAVListRequest{Server: server})
	require.NoError(t, err)
	require.Len(t, list.GetEntries(), 1)
	entry := list.GetEntries()[0]
	assert.Equal(t, "backup.json", entry.GetName())
	assert.EqualValues(t, len(content), entry.GetSize())
	assert.Positive(t, entry.GetModifiedUnixMs())

	downloadStream := &recordingWebDAVDownloadStream{ctx: t.Context()}
	err = service.WebDAVDownload(&husiv1.WebDAVDownloadRequest{
		Server: server,
		Name:   "backup.json",
	}, downloadStream)
	require.NoError(t, err)
	assert.Greater(t, len(downloadStream.sent), 1)
	for _, response := range downloadStream.sent {
		assert.LessOrEqual(t, len(response.GetChunk()), buf.BufferSize)
	}
	assert.Equal(t, content, downloadStream.content())

	_, err = service.WebDAVDelete(t.Context(), &husiv1.WebDAVDeleteRequest{
		Server: server,
		Name:   "backup.json",
	})
	require.NoError(t, err)
	list, err = service.WebDAVList(t.Context(), &husiv1.WebDAVListRequest{Server: server})
	require.NoError(t, err)
	assert.Empty(t, list.GetEntries())
}

func TestWebDAVServiceUploadRequiresHead(t *testing.T) {
	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)
	err := service.WebDAVUpload(&replayingWebDAVUploadStream{
		ctx:      t.Context(),
		requests: []*husiv1.WebDAVUploadRequest{uploadChunk([]byte("{}"))},
	})
	assert.Equal(t, codes.InvalidArgument, status.Code(err))
}

func TestWebDAVServiceUploadRejectsSecondHead(t *testing.T) {
	httpServer := httptest.NewServer(&xwebdav.Handler{
		FileSystem: xwebdav.NewMemFS(),
		LockSystem: xwebdav.NewMemLS(),
	})
	defer httpServer.Close()
	server := &husiv1.WebDAVServer{Url: httpServer.URL + "/husi"}
	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)

	stream := &replayingWebDAVUploadStream{
		ctx: t.Context(),
		requests: []*husiv1.WebDAVUploadRequest{
			uploadHead(server, "backup.json", 4),
			uploadChunk([]byte("{}")),
			uploadHead(server, "other.json", 0),
		},
	}
	err := service.WebDAVUpload(stream)
	assert.Equal(t, codes.InvalidArgument, status.Code(err))
	assert.False(t, stream.closed)
}

// The receiver stops once the PUT is gone; the handler must still return.
func TestWebDAVServiceUploadFailureEndsCall(t *testing.T) {
	httpServer := httptest.NewServer(http.HandlerFunc(func(writer http.ResponseWriter, _ *http.Request) {
		writer.WriteHeader(http.StatusUnauthorized)
	}))
	defer httpServer.Close()
	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)

	stream := &replayingWebDAVUploadStream{
		ctx: t.Context(),
		requests: []*husiv1.WebDAVUploadRequest{
			uploadHead(&husiv1.WebDAVServer{Url: httpServer.URL}, "backup.json", 4),
			uploadChunk([]byte("{}")),
			uploadChunk([]byte("{}")),
		},
	}
	err := service.WebDAVUpload(stream)
	assert.ErrorContains(t, err, "401")
	assert.False(t, stream.closed)
}

func TestWebDAVServiceInvalidName(t *testing.T) {
	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)
	server := &husiv1.WebDAVServer{Url: "http://127.0.0.1:1/husi"}

	err := service.WebDAVUpload(&replayingWebDAVUploadStream{
		ctx:      t.Context(),
		requests: []*husiv1.WebDAVUploadRequest{uploadHead(server, "../escape.json", 0)},
	})
	assert.Equal(t, codes.InvalidArgument, status.Code(err))

	err = service.WebDAVDownload(&husiv1.WebDAVDownloadRequest{
		Server: server,
		Name:   "../escape.json",
	}, &recordingWebDAVDownloadStream{ctx: t.Context()})
	assert.Equal(t, codes.InvalidArgument, status.Code(err))

	_, err = service.WebDAVDelete(t.Context(), &husiv1.WebDAVDeleteRequest{
		Server: server,
		Name:   "../escape.json",
	})
	assert.Equal(t, codes.InvalidArgument, status.Code(err))
}

func TestWebDAVServiceDownloadMissing(t *testing.T) {
	httpServer := httptest.NewServer(http.NotFoundHandler())
	defer httpServer.Close()
	service := NewApplicationService(nil, nil).(husiv1.ApplicationServiceServer)

	stream := &recordingWebDAVDownloadStream{ctx: t.Context()}
	err := service.WebDAVDownload(&husiv1.WebDAVDownloadRequest{
		Server: &husiv1.WebDAVServer{Url: httpServer.URL},
		Name:   "missing.json",
	}, stream)
	require.Error(t, err)
	assert.Empty(t, stream.sent)
}
