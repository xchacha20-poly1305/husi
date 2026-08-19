package coresvc

import (
	"context"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type applicationService struct {
	husiv1.UnimplementedApplicationServiceServer
	host *Host
}

func (s *applicationService) CheckConfig(ctx context.Context, req *husiv1.CheckConfigRequest) (*husiv1.CheckConfigResponse, error) {
	if s.host.checkConfig == nil {
		return nil, status.Error(codes.Internal, "check config not configured")
	}
	err := s.host.checkConfig(req.GetConfig())
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, err.Error())
	}
	return &husiv1.CheckConfigResponse{}, nil
}

func (s *applicationService) GenerateSchema(ctx context.Context, req *husiv1.GenerateSchemaRequest) (*husiv1.GenerateSchemaResponse, error) {
	if s.host.generateSchema == nil {
		return nil, status.Error(codes.Internal, "generate schema not configured")
	}
	schema, err := s.host.generateSchema(req.GetKind())
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, err.Error())
	}
	return &husiv1.GenerateSchemaResponse{Schema: schema}, nil
}

func (s *applicationService) StandaloneURLTest(ctx context.Context, req *husiv1.StandaloneURLTestRequest) (*husiv1.StandaloneURLTestResponse, error) {
	if s.host.standaloneURLTest == nil {
		return nil, status.Error(codes.Internal, "standalone url test not configured")
	}
	options := urlTestOptionsMask(req.GetOptions())
	timeoutMs := req.GetTimeoutMs()
	latency, err := s.host.standaloneURLTest(
		req.GetConfig(),
		req.GetOutboundTag(),
		req.GetLink(),
		timeoutMs,
		options,
		req.GetPlugins(),
	)
	if err != nil {
		return nil, wrapURLTestError(err)
	}
	return &husiv1.StandaloneURLTestResponse{LatencyMs: latency}, nil
}

func (s *applicationService) GetCert(ctx context.Context, req *husiv1.GetCertRequest) (*husiv1.GetCertResponse, error) {
	if s.host.getCert == nil {
		return nil, status.Error(codes.Internal, "get cert not configured")
	}
	pem, err := s.host.getCert(
		req.GetServer(),
		req.GetServerName(),
		req.GetMode(),
		req.GetSocksProxyUrl(),
	)
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, err.Error())
	}
	return &husiv1.GetCertResponse{Pem: pem}, nil
}

func (s *applicationService) STUNTest(req *husiv1.STUNTestRequest, stream husiv1.ApplicationService_STUNTestServer) error {
	if s.host.stunTest == nil {
		return status.Error(codes.Internal, "stun test not configured")
	}
	err := s.host.stunTest(
		stream.Context(),
		req.GetServer(),
		req.GetSocksProxyUrl(),
		func(resp *husiv1.STUNTestResponse) error {
			return stream.Send(resp)
		},
	)
	if err != nil {
		if stream.Context().Err() != nil {
			return status.FromContextError(stream.Context().Err()).Err()
		}
		return status.Error(codes.Unknown, err.Error())
	}
	return nil
}

func (s *applicationService) SpeedTest(req *husiv1.SpeedTestRequest, stream husiv1.ApplicationService_SpeedTestServer) error {
	if s.host.speedTest == nil {
		return status.Error(codes.Internal, "speed test not configured")
	}
	err := s.host.speedTest(
		stream.Context(),
		req,
		func(resp *husiv1.SpeedTestResponse) error {
			return stream.Send(resp)
		},
	)
	if err != nil {
		if stream.Context().Err() != nil {
			return status.FromContextError(stream.Context().Err()).Err()
		}
		return status.Error(codes.Unknown, err.Error())
	}
	return nil
}
