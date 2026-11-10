package demoapi

import (
	"context"
	"slices"
	"time"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/emptypb"
)

var clashModes = []string{"rule", "global", "direct"}

func (s *service) GetVersion(context.Context, *emptypb.Empty) (*daemon.Version, error) {
	return &daemon.Version{Version: C.Version + " (husi demo)", ApiVersion: daemon.APIVersion}, nil
}

func (s *service) GetStartedAt(context.Context, *emptypb.Empty) (*daemon.StartedAt, error) {
	return &daemon.StartedAt{StartedAt: s.startedAt.UnixMilli()}, nil
}

func (s *service) GetDefaultLogLevel(context.Context, *emptypb.Empty) (*daemon.DefaultLogLevel, error) {
	return &daemon.DefaultLogLevel{Level: daemon.LogLevel_INFO}, nil
}

func (s *service) SubscribeServiceStatus(_ *emptypb.Empty, stream grpc.ServerStreamingServer[daemon.ServiceStatus]) error {
	return watch(s, stream.Context(), stream.Send, func() *daemon.ServiceStatus {
		return &daemon.ServiceStatus{Status: daemon.ServiceStatus_STARTED}
	})
}

func (s *service) SubscribeGroups(_ *emptypb.Empty, stream grpc.ServerStreamingServer[daemon.Groups]) error {
	return watch(s, stream.Context(), stream.Send, func() *daemon.Groups {
		return &daemon.Groups{Group: s.groups}
	})
}

func (s *service) SubscribeOutbounds(_ *emptypb.Empty, stream grpc.ServerStreamingServer[daemon.OutboundList]) error {
	return watch(s, stream.Context(), stream.Send, func() *daemon.OutboundList {
		return &daemon.OutboundList{Outbounds: s.outbounds}
	})
}

func (s *service) GetClashModeStatus(context.Context, *emptypb.Empty) (*daemon.ClashModeStatus, error) {
	s.access.Lock()
	defer s.access.Unlock()
	return &daemon.ClashModeStatus{ModeList: slices.Clone(clashModes), CurrentMode: s.mode}, nil
}

func (s *service) SubscribeClashMode(_ *emptypb.Empty, stream grpc.ServerStreamingServer[daemon.ClashMode]) error {
	return watch(s, stream.Context(), stream.Send, func() *daemon.ClashMode {
		return &daemon.ClashMode{Mode: s.mode}
	})
}

func (s *service) SetClashMode(_ context.Context, request *daemon.ClashMode) (*emptypb.Empty, error) {
	if !slices.Contains(clashModes, request.Mode) {
		return nil, status.Error(codes.InvalidArgument, "unknown clash mode")
	}
	s.access.Lock()
	defer s.access.Unlock()
	if s.mode == request.Mode {
		return &emptypb.Empty{}, nil
	}
	s.mode = request.Mode
	s.appendLog(s.ctx, time.Now(), log.LevelInfo, "clash-mode", "updated mode: ", request.Mode)
	s.notify()
	return &emptypb.Empty{}, nil
}

func (s *service) SelectOutbound(_ context.Context, request *daemon.SelectOutboundRequest) (*emptypb.Empty, error) {
	s.access.Lock()
	defer s.access.Unlock()
	for _, group := range s.groups {
		if group.Tag != request.GroupTag {
			continue
		}
		if !group.Selectable {
			return nil, status.Error(codes.FailedPrecondition, "group is not selectable")
		}
		if !slices.ContainsFunc(group.Items, func(item *daemon.GroupItem) bool { return item.Tag == request.OutboundTag }) {
			return nil, status.Error(codes.InvalidArgument, "outbound is not a member of the group")
		}
		group.Selected = request.OutboundTag
		s.notify()
		return &emptypb.Empty{}, nil
	}
	return nil, status.Error(codes.NotFound, "group not found")
}

func (s *service) SetGroupExpand(_ context.Context, request *daemon.SetGroupExpandRequest) (*emptypb.Empty, error) {
	s.access.Lock()
	defer s.access.Unlock()
	for _, group := range s.groups {
		if group.Tag == request.GroupTag {
			group.IsExpand = request.IsExpand
			s.notify()
			return &emptypb.Empty{}, nil
		}
	}
	return nil, status.Error(codes.NotFound, "group not found")
}

func (s *service) URLTest(_ context.Context, request *daemon.URLTestRequest) (*emptypb.Empty, error) {
	s.access.Lock()
	defer s.access.Unlock()
	var items []*daemon.GroupItem
	loggerTag := ""
	logResults := false
	for _, group := range s.groups {
		if group.Tag == request.OutboundTag {
			items = group.Items
			logResults = true
			if group.Type == "urltest" {
				loggerTag = "outbound/urltest[" + group.Tag + "]"
			}
			break
		}
	}
	if items == nil {
		for _, item := range s.outbounds {
			if item.Tag == request.OutboundTag {
				items = []*daemon.GroupItem{item}
				break
			}
		}
	}
	if items == nil {
		return nil, status.Error(codes.NotFound, "outbound not found")
	}
	now := time.Now()
	for _, item := range items {
		item.UrlTestTime = now.Unix()
		item.UrlTestDelay = 12 + (item.UrlTestDelay+17)%180
		if logResults {
			s.appendLog(s.ctx, now, log.LevelDebug, loggerTag, "outbound ", item.Tag, " available: ", item.UrlTestDelay, "ms")
		}
	}
	for _, group := range s.groups {
		if group.Type == "urltest" {
			best := slices.MinFunc(group.Items, func(a, b *daemon.GroupItem) int {
				return int(a.UrlTestDelay - b.UrlTestDelay)
			})
			group.Selected = best.Tag
		}
	}
	s.notify()
	return &emptypb.Empty{}, nil
}

func (s *service) CloseConnection(_ context.Context, request *daemon.CloseConnectionRequest) (*emptypb.Empty, error) {
	s.access.Lock()
	defer s.access.Unlock()
	for _, connection := range s.connections {
		if connection.Id == request.Id {
			if connection.ClosedAt == 0 {
				s.closeConnection(connection, time.Now())
				s.trimConnections()
				s.notify()
			}
			return &emptypb.Empty{}, nil
		}
	}
	return nil, status.Error(codes.NotFound, "connection not found")
}

func (s *service) CloseAllConnections(context.Context, *emptypb.Empty) (*emptypb.Empty, error) {
	s.access.Lock()
	defer s.access.Unlock()
	now := time.Now()
	for _, connection := range s.connections {
		if connection.ClosedAt == 0 {
			s.closeConnection(connection, now)
		}
	}
	s.trimConnections()
	s.notify()
	return &emptypb.Empty{}, nil
}

func (s *service) ClearLogs(context.Context, *emptypb.Empty) (*emptypb.Empty, error) {
	s.access.Lock()
	defer s.access.Unlock()
	s.logs = nil
	s.logGeneration++
	s.notify()
	return &emptypb.Empty{}, nil
}

func (s *service) SubscribeTailscaleStatus(_ *emptypb.Empty, stream grpc.ServerStreamingServer[daemon.TailscaleStatusUpdate]) error {
	return watch(s, stream.Context(), stream.Send, func() *daemon.TailscaleStatusUpdate {
		return &daemon.TailscaleStatusUpdate{}
	})
}

func (s *service) SubscribeOpenConnectStatus(_ *emptypb.Empty, stream grpc.ServerStreamingServer[daemon.OpenConnectStatusUpdate]) error {
	return watch(s, stream.Context(), stream.Send, func() *daemon.OpenConnectStatusUpdate {
		return &daemon.OpenConnectStatusUpdate{}
	})
}

func (s *service) SubscribeOpenVPNStatus(_ *emptypb.Empty, stream grpc.ServerStreamingServer[daemon.OpenVPNStatusUpdate]) error {
	return watch(s, stream.Context(), stream.Send, func() *daemon.OpenVPNStatusUpdate {
		return &daemon.OpenVPNStatusUpdate{}
	})
}
