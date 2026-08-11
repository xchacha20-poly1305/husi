package coreclient

import (
	"bytes"
	"context"
	"errors"
	"io"
	"sync"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/encoding"
	"google.golang.org/grpc/health/grpc_health_v1"
	"google.golang.org/grpc/status"
)

var _ encoding.Codec = rawCodec{}

// rawCodec is a passthrough encoding.Codec whose Name reports "proto". The
// wire stays ordinary grpc+proto so the server keeps its generated stubs; the
// client side skips marshal/unmarshal. Applied per-call via ForceCodec — never
// via encoding.RegisterCodec, which would leak into the in-process server.
type rawCodec struct{}

func (rawCodec) Marshal(v any) ([]byte, error) {
	if v == nil {
		return nil, nil
	}
	data, isBytes := v.([]byte)
	if !isBytes {
		return nil, E.New("rawCodec: expected []byte")
	}
	return data, nil
}

func (rawCodec) Unmarshal(data []byte, v any) error {
	if v == nil {
		return E.New("rawCodec: nil target")
	}
	ptr, isBytesPtr := v.(*[]byte)
	if !isBytesPtr {
		return E.New("rawCodec: expected *[]byte")
	}
	*ptr = bytes.Clone(data)
	return nil
}

func (rawCodec) Name() string {
	return "proto"
}

var forceRawCodec = grpc.ForceCodec(rawCodec{})

type Client struct {
	access sync.Mutex
	conn   *grpc.ClientConn
	path   string
}

func Dial(endpoint string) (*Client, error) {
	if endpoint == "" {
		return nil, E.New("missing socket path")
	}
	conn, err := grpc.NewClient(
		grpcTarget(endpoint),
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithContextDialer(contextDialer(endpoint)),
	)
	if err != nil {
		return nil, E.Cause(err, "dial grpc")
	}
	return &Client{conn: conn, path: endpoint}, nil
}

// Invoke performs a unary RPC. request is the serialized protobuf body;
// the response body is returned as raw bytes.
func (c *Client) Invoke(ctx context.Context, method string, request []byte) ([]byte, error) {
	c.access.Lock()
	conn := c.conn
	c.access.Unlock()
	if conn == nil {
		return nil, E.New("client closed")
	}
	var response []byte
	err := conn.Invoke(ctx, method, request, &response, forceRawCodec, grpc.WaitForReady(true))
	if err != nil {
		return nil, err
	}
	return response, nil
}

type StreamHandler interface {
	OnMessage(message []byte)
	OnClosed(errMessage string)
}

type Stream struct {
	cancel context.CancelFunc
	done   chan struct{}
}

func (s *Stream) Close() {
	s.cancel()
	<-s.done
}

// Stream starts a server-streaming RPC. handler is invoked from a background
// goroutine; Stream.Close cancels it.
func (c *Client) Stream(ctx context.Context, method string, request []byte, handler StreamHandler) (*Stream, error) {
	c.access.Lock()
	conn := c.conn
	c.access.Unlock()
	if conn == nil {
		return nil, E.New("client closed")
	}

	streamCtx, cancel := context.WithCancel(ctx)
	desc := &grpc.StreamDesc{ServerStreams: true}
	clientStream, err := conn.NewStream(streamCtx, desc, method, forceRawCodec, grpc.WaitForReady(true))
	if err != nil {
		cancel()
		return nil, err
	}
	if err = clientStream.SendMsg(request); err != nil {
		cancel()
		return nil, E.Cause(err, "send stream request")
	}
	if err = clientStream.CloseSend(); err != nil {
		cancel()
		return nil, E.Cause(err, "close stream send")
	}

	done := make(chan struct{})
	go func() {
		defer close(done)
		var errMessage string
		for {
			var msg []byte
			recvErr := clientStream.RecvMsg(&msg)
			if recvErr != nil {
				// Clean end-of-stream is not an error (server finished).
				if streamCtx.Err() == nil && !errors.Is(recvErr, io.EOF) {
					if errStatus, loaded := status.FromError(recvErr); loaded {
						errMessage = errStatus.Code().String() + ": " + errStatus.Message()
					} else {
						errMessage = recvErr.Error()
					}
				}
				break
			}
			handler.OnMessage(msg)
		}
		handler.OnClosed(errMessage)
	}()

	return &Stream{cancel: cancel, done: done}, nil
}

func (c *Client) Probe(ctx context.Context) error {
	c.access.Lock()
	conn := c.conn
	c.access.Unlock()
	if conn == nil {
		return E.New("client closed")
	}
	client := grpc_health_v1.NewHealthClient(conn)
	resp, err := client.Check(ctx, &grpc_health_v1.HealthCheckRequest{}, grpc.WaitForReady(false))
	if err != nil {
		return err
	}
	if resp.GetStatus() != grpc_health_v1.HealthCheckResponse_SERVING {
		return E.New("health status: ", resp.GetStatus().String())
	}
	return nil
}

func (c *Client) Close() error {
	c.access.Lock()
	defer c.access.Unlock()
	conn := c.conn
	c.conn = nil
	return common.Close(common.PtrOrNil(conn))
}
