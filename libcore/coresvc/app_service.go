package coresvc

import (
	"context"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
)

type appService struct {
	husiv1.UnimplementedAppServiceServer
	host *Host
}

func (s *appService) ShowWindow(ctx context.Context, _ *husiv1.ShowWindowRequest) (*husiv1.ShowWindowResponse, error) {
	handler := s.host.getAppHandler()
	if handler != nil {
		handler.OnShowWindow()
	}
	return &husiv1.ShowWindowResponse{}, nil
}

func (s *appService) DispatchDeepLinks(ctx context.Context, req *husiv1.DispatchDeepLinksRequest) (*husiv1.DispatchDeepLinksResponse, error) {
	handler := s.host.getAppHandler()
	if handler != nil {
		handler.OnDispatchDeepLinks(req.GetLinks())
	}
	return &husiv1.DispatchDeepLinksResponse{}, nil
}

func (s *appService) RunTask(ctx context.Context, req *husiv1.RunTaskRequest) (*husiv1.RunTaskResponse, error) {
	handler := s.host.getAppHandler()
	taskID := req.GetTaskId()
	if handler != nil && taskID != "" {
		handler.OnRunTask(taskID)
	}
	return &husiv1.RunTaskResponse{}, nil
}
