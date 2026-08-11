package libcore

import (
	"context"
	"time"

	"libcore/coreclient"
	"libcore/coresvc"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	"google.golang.org/grpc/status"
)

type BridgeClient struct {
	client *coreclient.Client
}

func NewBridgeClient(basePath string) (*BridgeClient, error) {
	if basePath == "" {
		basePath = internalAssetsPath
	}
	client, err := coreclient.Dial(coresvc.ClientEndpoint(basePath))
	if err != nil {
		return nil, err
	}
	return &BridgeClient{client: client}, nil
}

const defaultBridgeTimeout = 10 * time.Second

func (c *BridgeClient) Call(method string, request []byte) ([]byte, error) {
	ctx, cancel := context.WithTimeout(context.Background(), defaultBridgeTimeout)
	defer cancel()
	return c.invoke(ctx, method, request)
}

func (c *BridgeClient) CallWithTimeout(method string, request []byte, timeoutMs int32) ([]byte, error) {
	timeout := time.Duration(timeoutMs) * time.Millisecond
	if timeout <= 0 {
		timeout = 10 * time.Second
	}
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	return c.invoke(ctx, method, request)
}

func (c *BridgeClient) invoke(ctx context.Context, method string, request []byte) ([]byte, error) {
	resp, err := c.client.Invoke(ctx, method, request)
	if err != nil {
		return nil, wrapBridgeRPCError(err)
	}
	return resp, nil
}

// wrapBridgeRPCError formats gRPC status errors as "<Code>: <message>" so the
// Kotlin CoreClient can split them without string archaeology on the grpc-go
// default "rpc error: code = … desc = …" form.
func wrapBridgeRPCError(err error) error {
	if err == nil {
		return nil
	}
	if errStatus, loaded := status.FromError(err); loaded {
		return E.New(errStatus.Code().String(), ": ", errStatus.Message())
	}
	return err
}

func (c *BridgeClient) Probe() error {
	ctx, cancel := context.WithTimeout(context.Background(), defaultBridgeTimeout)
	defer cancel()
	return wrapBridgeRPCError(c.client.Probe(ctx))
}

func (c *BridgeClient) Close() error {
	return c.client.Close()
}

type StreamHandler interface {
	OnMessage(message []byte)
	OnClosed(message string)
}

type StreamCall struct {
	stream *coreclient.Stream
}

func (s *StreamCall) Close() {
	_ = common.Close(common.PtrOrNil(s.stream))
}

type streamHandlerAdapter struct {
	handler StreamHandler
}

func (a streamHandlerAdapter) OnMessage(message []byte) {
	a.handler.OnMessage(message)
}

func (a streamHandlerAdapter) OnClosed(errMessage string) {
	a.handler.OnClosed(errMessage)
}

func (c *BridgeClient) Stream(method string, request []byte, handler StreamHandler) (*StreamCall, error) {
	stream, err := c.client.Stream(context.Background(), method, request, streamHandlerAdapter{handler: handler})
	if err != nil {
		return nil, err
	}
	return &StreamCall{stream: stream}, nil
}
