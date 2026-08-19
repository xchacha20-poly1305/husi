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
	err := s.host.backend.CheckConfig(req.GetConfig())
	if err != nil {
		return nil, backendError(err, codes.InvalidArgument)
	}
	return &husiv1.CheckConfigResponse{}, nil
}

func (s *applicationService) GenerateSchema(ctx context.Context, req *husiv1.GenerateSchemaRequest) (*husiv1.GenerateSchemaResponse, error) {
	schema, err := s.host.backend.GenerateSchema(req.GetKind())
	if err != nil {
		return nil, backendError(err, codes.InvalidArgument)
	}
	return &husiv1.GenerateSchemaResponse{Schema: schema}, nil
}

func (s *applicationService) StandaloneURLTest(ctx context.Context, req *husiv1.StandaloneURLTestRequest) (*husiv1.StandaloneURLTestResponse, error) {
	options := urlTestOptionsMask(req.GetOptions())
	timeoutMs := req.GetTimeoutMs()
	latency, err := s.host.backend.StandaloneURLTest(
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
	pem, err := s.host.backend.GetCert(
		ctx,
		req.GetServer(),
		req.GetServerName(),
		req.GetMode(),
		req.GetSocksProxyUrl(),
	)
	if err != nil {
		return nil, backendError(err, codes.InvalidArgument)
	}
	return &husiv1.GetCertResponse{Pem: pem}, nil
}

func (s *applicationService) STUNTest(req *husiv1.STUNTestRequest, stream husiv1.ApplicationService_STUNTestServer) error {
	err := s.host.backend.STUNTest(stream.Context(), req.GetServer(), req.GetSocksProxyUrl(), stream)
	if err != nil {
		if stream.Context().Err() != nil {
			return status.FromContextError(stream.Context().Err()).Err()
		}
		return backendError(err, codes.Unknown)
	}
	return nil
}

func (s *applicationService) SpeedTest(req *husiv1.SpeedTestRequest, stream husiv1.ApplicationService_SpeedTestServer) error {
	err := s.host.backend.SpeedTest(stream.Context(), req, stream)
	if err != nil {
		if stream.Context().Err() != nil {
			return status.FromContextError(stream.Context().Err()).Err()
		}
		return backendError(err, codes.Unknown)
	}
	return nil
}
