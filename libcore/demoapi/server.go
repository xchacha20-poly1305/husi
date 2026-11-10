// Package demoapi serves a simulated StartedService without a proxy core or user configuration.
package demoapi

import (
	"cmp"
	"context"
	"net"
	"net/netip"
	"sync"
	"time"

	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json/badoption"
	N "github.com/sagernet/sing/common/network"

	"github.com/xchacha20-poly1305/husi/libcore/v2/externalapi"
	"google.golang.org/grpc"
	"google.golang.org/grpc/health"
	"google.golang.org/grpc/health/grpc_health_v1"
)

const DefaultListenAddress = "127.0.0.1:9090"

type Options struct {
	Listen string
	Secret string
}

type Server struct {
	api       *externalapi.Server
	grpc      *grpc.Server
	cancel    context.CancelFunc
	done      chan struct{}
	closeOnce sync.Once
	closeErr  error
}

func Start(ctx context.Context, logger log.ContextLogger, options Options) (*Server, error) {
	err := ctx.Err()
	if err != nil {
		return nil, err
	}
	options.Listen = cmp.Or(options.Listen, DefaultListenAddress)
	address, err := net.ResolveTCPAddr(N.NetworkTCP, options.Listen)
	if err != nil {
		return nil, E.Cause(err, "parse demo listen address")
	}
	addr := address.AddrPort().Addr()
	if !addr.IsValid() {
		addr = netip.IPv4Unspecified()
	}
	ctx, cancel := context.WithCancel(ctx)
	service := newService(ctx, time.Now())
	grpcServer := grpc.NewServer(
		grpc.ChainUnaryInterceptor(externalapi.UnaryAuthInterceptor(options.Secret)),
		grpc.ChainStreamInterceptor(externalapi.StreamAuthInterceptor(options.Secret)),
	)
	daemon.RegisterStartedServiceServer(grpcServer, service)
	healthServer := health.NewServer()
	healthServer.SetServingStatus("", grpc_health_v1.HealthCheckResponse_SERVING)
	healthServer.SetServingStatus(daemon.StartedService_ServiceDesc.ServiceName, grpc_health_v1.HealthCheckResponse_SERVING)
	grpc_health_v1.RegisterHealthServer(grpcServer, healthServer)
	api, err := externalapi.New(ctx, logger, externalapi.Options{
		Listen:                           new(badoption.Addr(addr)),
		ListenPort:                       uint16(address.Port),
		Secret:                           options.Secret,
		AccessControlAllowPrivateNetwork: true,
	}, grpcServer)
	if err == nil {
		err = api.Start()
	}
	if err != nil {
		cancel()
		if api != nil {
			api.Close()
		}
		grpcServer.Stop()
		return nil, E.Cause(err, "start demo API")
	}
	server := &Server{api: api, grpc: grpcServer, cancel: cancel, done: make(chan struct{})}
	go func() {
		defer close(server.done)
		service.run()
	}()
	return server, nil
}

func (s *Server) Addr() string {
	return s.api.Addr()
}

func (s *Server) Close() error {
	s.closeOnce.Do(func() {
		s.cancel()
		s.closeErr = s.api.Close()
		s.grpc.Stop()
		<-s.done
	})
	return s.closeErr
}
