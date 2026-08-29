package coresvc

import (
	"context"
	"time"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/daemon"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/urltest"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type coreService struct {
	husiv1.UnimplementedCoreServiceServer
	host *Host
}

func (s *coreService) GetVersion(ctx context.Context, _ *husiv1.GetVersionRequest) (*husiv1.GetVersionResponse, error) {
	return &husiv1.GetVersionResponse{
		Version:          s.host.version,
		SingBoxVersion:   C.Version,
		BuildEnvironment: s.host.buildEnvironment,
		ApiVersion:       daemon.APIVersion,
	}, nil
}

func (s *coreService) URLTest(ctx context.Context, req *husiv1.URLTestRequest) (*husiv1.URLTestResponse, error) {
	if s.host.started.Instance() == nil {
		return nil, status.Error(codes.FailedPrecondition, "instance not created")
	}
	options := urltest.FlagsFromProto(req.GetOptions())
	timeoutMs := req.GetTimeoutMs()
	if timeoutMs <= 0 {
		timeoutMs = int32(C.TCPTimeout / time.Millisecond)
	}

	latency, err := defaultURLTest(s.host, req.GetOutboundTag(), req.GetLink(), timeoutMs, options)
	if err != nil {
		return nil, urltest.WrapError(err)
	}
	return &husiv1.URLTestResponse{LatencyMs: latency}, nil
}

func (s *coreService) ResetNetwork(ctx context.Context, _ *husiv1.ResetNetworkRequest) (*husiv1.ResetNetworkResponse, error) {
	instance := s.host.started.Instance()
	if instance == nil {
		return nil, status.Error(codes.FailedPrecondition, "instance not created")
	}
	instance.Box().Network().ResetNetwork(s.host.InstanceContext())
	return &husiv1.ResetNetworkResponse{}, nil
}

func (s *coreService) SubscribeServiceEvents(
	_ *husiv1.SubscribeServiceEventsRequest,
	stream husiv1.CoreService_SubscribeServiceEventsServer,
) error {
	events, unsubscribe := s.host.events.Subscribe()
	defer unsubscribe()
	for {
		select {
		case <-stream.Context().Done():
			return nil
		case event, ok := <-events:
			if !ok {
				return nil
			}
			if err := stream.Send(event); err != nil {
				return err
			}
		}
	}
}

func defaultURLTest(host *Host, tag, link string, timeoutMs int32, options urltest.Flags) (int32, error) {
	instanceCtx := host.holder.Get()
	if instanceCtx == nil {
		return -1, E.New("instance context not available")
	}
	return urltest.RunTag(
		instanceCtx,
		service.FromContext[adapter.OutboundManager](instanceCtx),
		tag,
		link,
		timeoutMs,
		options,
	)
}
