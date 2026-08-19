package coresvc

import (
	"context"
	"errors"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/protocol/group"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

var ErrOutboundNotFound = E.New("outbound is not found")

type coreService struct {
	husiv1.UnimplementedCoreServiceServer
	host *Host
}

func (s *coreService) GetVersion(ctx context.Context, _ *husiv1.GetVersionRequest) (*husiv1.GetVersionResponse, error) {
	buildEnv := ""
	if s.host.buildEnvironment != nil {
		buildEnv = s.host.buildEnvironment()
	}
	return &husiv1.GetVersionResponse{
		Version:          s.host.version,
		SingBoxVersion:   C.Version,
		BuildEnvironment: buildEnv,
		ApiVersion:       daemon.APIVersion,
	}, nil
}

func (s *coreService) URLTest(ctx context.Context, req *husiv1.URLTestRequest) (*husiv1.URLTestResponse, error) {
	if s.host.started.Instance() == nil {
		return nil, status.Error(codes.FailedPrecondition, "instance not created")
	}
	options := urlTestOptionsMask(req.GetOptions())
	timeoutMs := req.GetTimeoutMs()
	if timeoutMs <= 0 {
		timeoutMs = int32(C.TCPTimeout / time.Millisecond)
	}

	latency, err := defaultURLTest(s.host, req.GetOutboundTag(), req.GetLink(), timeoutMs, options)
	if err != nil {
		return nil, wrapURLTestError(err)
	}
	return &husiv1.URLTestResponse{LatencyMs: latency}, nil
}

func (s *coreService) ResetNetwork(ctx context.Context, _ *husiv1.ResetNetworkRequest) (*husiv1.ResetNetworkResponse, error) {
	instance := s.host.started.Instance()
	if instance == nil {
		return nil, status.Error(codes.FailedPrecondition, "instance not created")
	}
	instance.Box().Network().ResetNetwork()
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

func urlTestOptionsMask(opts *husiv1.URLTestOptions) uint8 {
	if opts == nil {
		return 0
	}
	var mask uint8
	if opts.GetUnifiedDelay() {
		mask |= URLTestUnifiedDelay
	}
	if opts.GetIgnoreHandshakeTime() {
		mask |= URLTestIgnoreHandshakeTime
	}
	return mask
}

func wrapURLTestError(err error) error {
	if err == nil {
		return nil
	}
	if E.IsCanceled(err) {
		return status.Error(codes.DeadlineExceeded, err.Error())
	}
	if errors.Is(err, ErrOutboundNotFound) {
		return status.Error(codes.NotFound, err.Error())
	}
	return status.Error(codes.Internal, err.Error())
}

// defaultURLTest resolves OutboundManager / HistoryStorage from the holder context.
func defaultURLTest(host *Host, tag, link string, timeoutMs int32, options uint8) (int32, error) {
	instanceCtx := host.holder.Get()
	if instanceCtx == nil {
		return -1, E.New("instance context not available")
	}
	outboundManager := service.FromContext[adapter.OutboundManager](instanceCtx)
	if outboundManager == nil {
		return -1, E.New("outbound manager not available")
	}
	var detour adapter.Outbound
	if tag == "" {
		detour = outboundManager.Default()
	} else {
		var loaded bool
		detour, loaded = outboundManager.Outbound(tag)
		if !loaded {
			return -1, E.Cause(ErrOutboundNotFound, tag)
		}
	}

	return RunOutboundURLTest(instanceCtx, detour, link, timeoutMs, options)
}

// RunOutboundURLTest is the shared goroutine+timeout+history block used by
// defaultURLTest and the forTest boxInstance.urlTest path.
func RunOutboundURLTest(instanceCtx context.Context, detour adapter.Outbound, link string, timeoutMs int32, options uint8) (int32, error) {
	ctx, cancel := context.WithTimeout(instanceCtx, time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()

	chLatency := make(chan uint16, 1)
	var testErr error
	go func() {
		var t uint16
		t, testErr = URLTest(ctx, link, detour, options)
		if testErr != nil {
			close(chLatency)
			return
		}
		chLatency <- t

		historyStorage := service.PtrFromContext[urltest.HistoryStorage](instanceCtx)
		if historyStorage == nil {
			return
		}
		realTag := group.RealTag(detour)
		historyStorage.StoreURLTestHistory(realTag, &adapter.URLTestHistory{
			Time:  time.Now(),
			Delay: t,
		})
	}()
	select {
	case <-ctx.Done():
		return -1, ctx.Err()
	case t, loaded := <-chLatency:
		if !loaded {
			return -1, testErr
		}
		return int32(t), nil
	}
}
