package libcore

import (
	"path/filepath"
	"sync"
	"testing"
	"time"

	"github.com/sagernet/sing-box/daemon"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"google.golang.org/protobuf/proto"
)

const subscribeStatusMethod = "/daemon.StartedService/SubscribeStatus"

type recordingStreamHandler struct {
	access   sync.Mutex
	messages [][]byte
	closed   bool
	closeErr string
	done     chan struct{}
}

func newRecordingStreamHandler() *recordingStreamHandler {
	return &recordingStreamHandler{done: make(chan struct{})}
}

func (h *recordingStreamHandler) OnMessage(message []byte) {
	h.access.Lock()
	defer h.access.Unlock()
	h.messages = append(h.messages, message)
}

func (h *recordingStreamHandler) OnClosed(errMessage string) {
	h.access.Lock()
	defer h.access.Unlock()
	if h.closed {
		return
	}
	h.closed = true
	h.closeErr = errMessage
	close(h.done)
}

func (h *recordingStreamHandler) messageCount() int {
	h.access.Lock()
	defer h.access.Unlock()
	return len(h.messages)
}

func newTestBridgeClient(t *testing.T) *BridgeClient {
	t.Helper()
	_, socketPath := startLibcoreHost(t)
	client, err := NewBridgeClient(filepath.Dir(socketPath))
	require.NoError(t, err)
	t.Cleanup(func() { _ = client.Close() })
	require.NoError(t, client.Probe())
	return client
}

func subscribeStatusRequest(t *testing.T) []byte {
	t.Helper()
	request, err := proto.Marshal(&daemon.SubscribeStatusRequest{Interval: int64(50 * time.Millisecond)})
	require.NoError(t, err)
	return request
}

// The JVM binding frees its view of the request as soon as Stream returns,
// so the bytes gRPC writes asynchronously must not be the caller's.
func TestBridgeClientStreamCopiesRequest(t *testing.T) {
	client := newTestBridgeClient(t)
	request := subscribeStatusRequest(t)
	handler := newRecordingStreamHandler()

	call, err := client.Stream(subscribeStatusMethod, request, handler)
	require.NoError(t, err)
	t.Cleanup(call.Close)
	for index := range request {
		request[index] = 0xFF
	}

	assert.Eventually(t, func() bool {
		return handler.messageCount() > 0
	}, 5*time.Second, 10*time.Millisecond, "no status sample: %s", handler.closeErr)
}

func TestBridgeClientStreamCloseEndsStream(t *testing.T) {
	client := newTestBridgeClient(t)
	handler := newRecordingStreamHandler()

	call, err := client.Stream(subscribeStatusMethod, subscribeStatusRequest(t), handler)
	require.NoError(t, err)
	require.Eventually(t, func() bool {
		return handler.messageCount() > 0
	}, 5*time.Second, 10*time.Millisecond, "stream never started")

	call.Close()

	select {
	case <-handler.done:
	case <-time.After(5 * time.Second):
		t.Fatal("Close did not end the stream")
	}
	countAfterClose := handler.messageCount()
	time.Sleep(200 * time.Millisecond)
	assert.Equal(t, countAfterClose, handler.messageCount(), "stream kept sending after Close")
}
