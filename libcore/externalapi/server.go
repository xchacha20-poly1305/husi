// Package externalapi is another sing-box API service implementation.
// Removed dashboard and other complex settings.
package externalapi

import (
	"context"
	"net"
	"net/http"
	"net/netip"

	"github.com/sagernet/sing-box/common/listener"
	"github.com/sagernet/sing-box/common/tls"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"
	aTLS "github.com/sagernet/sing/common/tls"

	"golang.org/x/net/http2"
	"google.golang.org/grpc"
)

type Server struct {
	ctx        context.Context
	cancel     context.CancelFunc
	logger     log.ContextLogger
	options    Options
	listener   *listener.Listener
	tlsConfig  tls.ServerConfig
	grpcServer *grpc.Server
	httpServer *http.Server
	addr       string
}

func New(ctx context.Context, logger log.ContextLogger, options Options, grpcServer *grpc.Server) (*Server, error) {
	err := options.Validate()
	if err != nil {
		return nil, err
	}
	ctx, cancel := context.WithCancel(ctx)
	s := &Server{
		ctx:        ctx,
		cancel:     cancel,
		logger:     logger,
		options:    options,
		grpcServer: grpcServer,
		listener: listener.New(listener.Options{
			Context: ctx,
			Logger:  logger,
			Network: []string{N.NetworkTCP},
			Listen:  options.ListenOptions,
		}),
	}
	if options.TLS != nil {
		tlsConfig, err := tls.NewServer(ctx, logger, common.PtrValueOrDefault(options.TLS))
		if err != nil {
			cancel()
			return nil, err
		}
		s.tlsConfig = tlsConfig
	}
	protocols := new(http.Protocols)
	protocols.SetHTTP1(true)
	protocols.SetHTTP2(true)
	protocols.SetUnencryptedHTTP2(true)
	s.httpServer = &http.Server{
		Handler: newHTTPHandler(logger, grpcServer, options),
		BaseContext: func(net.Listener) context.Context {
			return s.ctx
		},
		Protocols: protocols,
	}
	return s, nil
}

func (s *Server) Start() error {
	if s.tlsConfig != nil {
		err := s.tlsConfig.Start()
		if err != nil {
			return E.Cause(err, "create TLS config")
		}
		if !common.Contains(s.tlsConfig.NextProtos(), http2.NextProtoTLS) {
			s.tlsConfig.SetNextProtos(append([]string{http2.NextProtoTLS}, s.tlsConfig.NextProtos()...))
		}
		if !common.Contains(s.tlsConfig.NextProtos(), "http/1.1") {
			s.tlsConfig.SetNextProtos(append(s.tlsConfig.NextProtos(), "http/1.1"))
		}
	}
	tcpListener, err := s.listener.ListenTCP()
	if err != nil {
		return err
	}
	if s.tlsConfig != nil {
		tcpListener = aTLS.NewListener(tcpListener, s.tlsConfig)
	}
	s.addr = tcpListener.Addr().String()
	if s.options.Secret == "" && !listenIsLoopback(s.options) {
		s.logger.Warn("external API is listening on ", s.addr, " without a secret; this exposes the full daemon API to the network")
	}
	go func() {
		serveErr := s.httpServer.Serve(tcpListener)
		if serveErr != nil && !E.IsClosedOrCanceled(serveErr) {
			s.logger.Error("serve error: ", serveErr)
		}
	}()
	return nil
}

func listenIsLoopback(options Options) bool {
	return options.Listen.Build(netip.AddrFrom4([4]byte{127, 0, 0, 1})).IsLoopback()
}

func (s *Server) Addr() string {
	return s.addr
}

func (s *Server) Close() error {
	s.cancel()
	return common.Close(
		common.PtrOrNil(s.httpServer),
		common.PtrOrNil(s.listener),
		s.tlsConfig,
	)
}
