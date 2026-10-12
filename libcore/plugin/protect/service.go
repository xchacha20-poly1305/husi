// Package protect serves the socket protect path protocol of
// github.com/xchacha20-poly1305/protect-go as a sing-box service, so plugins
// can keep their sockets out of the TUN.
package protect

import (
	"context"

	"github.com/sagernet/sing-box/adapter"
	boxService "github.com/sagernet/sing-box/adapter/service"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/pluginoption"
	"github.com/xchacha20-poly1305/protect-go"
)

func RegisterService(registry *boxService.Registry) {
	boxService.Register[pluginoption.ProtectServiceOptions](registry, pluginoption.TypeProtect, NewService)
}

var _ adapter.Service = (*Service)(nil)

type Service struct {
	boxService.Adapter
	ctx    context.Context
	logger log.ContextLogger
	server *protect.Server
	path   string
}

func NewService(ctx context.Context, logger log.ContextLogger, tag string, options pluginoption.ProtectServiceOptions) (adapter.Service, error) {
	if options.Path == "" {
		return nil, E.New("missing path")
	}
	protector := service.FromContext[protect.Protector](ctx)
	if protector == nil {
		networkManager := service.FromContext[adapter.NetworkManager](ctx)
		if networkManager == nil {
			return nil, E.New("missing network manager")
		}
		protector = networkManagerProtector{networkManager: networkManager}
	}
	server := &protect.Server{
		Protector: protector,
		OnError: func(err error) {
			logger.ErrorContext(ctx, err)
		},
	}
	if options.VerifyPeer {
		server.Verify = verifyPluginPeer
	}
	return &Service{
		Adapter: boxService.NewAdapter(pluginoption.TypeProtect, tag),
		ctx:     ctx,
		logger:  logger,
		server:  server,
		path:    options.Path,
	}, nil
}

func (s *Service) Start(stage adapter.StartStage, scope *adapter.Scope) error {
	if stage != adapter.StartStateStart {
		return nil
	}
	listener, err := protect.Listen(s.path)
	if err != nil {
		return E.Cause(err, "listen protect path")
	}
	scope.Add(listener.Close)
	go func() {
		err := s.server.Serve(listener)
		if err != nil {
			s.logger.ErrorContext(s.ctx, err)
		}
	}()
	return nil
}
