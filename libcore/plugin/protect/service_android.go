package protect

import (
	"cmp"
	"context"
	"net"
	"os"

	"github.com/sagernet/sing-box/adapter"
	boxService "github.com/sagernet/sing-box/adapter/service"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/pluginoption"
	"golang.org/x/sys/unix"
)

func RegisterService(registry *boxService.Registry) {
	boxService.Register[pluginoption.ProtectServiceOptions](registry, pluginoption.TypeProtect, NewService)
}

var _ adapter.Service = (*Service)(nil)

type Service struct {
	boxService.Adapter
	ctx       context.Context
	logger    log.ContextLogger
	protector Protector
	listener  *net.UnixListener
	path      string
}

func NewService(ctx context.Context, logger log.ContextLogger, tag string, options pluginoption.ProtectServiceOptions) (adapter.Service, error) {
	if options.Path == "" {
		return nil, E.New("missing path")
	}
	protector := service.FromContext[Protector](ctx)
	if protector == nil {
		return nil, E.New("missing platform protector")
	}
	return &Service{
		Adapter:   boxService.NewAdapter(pluginoption.TypeProtect, tag),
		ctx:       ctx,
		logger:    logger,
		protector: protector,
		path:      options.Path,
	}, nil
}

func (s *Service) Start(stage adapter.StartStage) error {
	if stage != adapter.StartStateStart {
		return nil
	}
	_ = os.Remove(s.path)
	var listenConfig net.ListenConfig
	listener, err := listenConfig.Listen(s.ctx, "unix", s.path)
	if err != nil {
		return err
	}
	_ = os.Chmod(s.path, os.ModePerm)
	s.listener = listener.(*net.UnixListener)
	go s.loop()
	return nil
}

func (s *Service) loop() {
	for {
		select {
		case <-s.ctx.Done():
			return
		default:
		}
		conn, err := s.listener.AcceptUnix()
		if err != nil {
			if !E.IsClosedOrCanceled(err) {
				s.logger.ErrorContext(s.ctx, err)
			}
			return
		}
		go s.handle(conn)
	}
}

const (
	protectFailed byte = iota
	protectSuccess
)

func (s *Service) handle(conn *net.UnixConn) {
	defer conn.Close()
	rawConn, err := conn.SyscallConn()
	if err != nil {
		_, _ = conn.Write([]byte{protectFailed})
		s.logger.ErrorContext(s.ctx, E.Cause(err, "SyscallConn"))
		return
	}
	var receivedFd int
	controlErr := rawConn.Control(func(fd uintptr) {
		buf := make([]byte, unix.CmsgSpace(4))
		_, _, _, _, err = unix.Recvmsg(int(fd), nil, buf, 0)
		if err != nil {
			return
		}
		var controlMessages []unix.SocketControlMessage
		controlMessages, err = unix.ParseSocketControlMessage(buf)
		if err != nil {
			return
		}
		if len(controlMessages) != 1 {
			err = E.New("invalid control messages count: ", len(controlMessages))
			return
		}
		var fds []int
		fds, err = unix.ParseUnixRights(&controlMessages[0])
		if err != nil {
			err = E.Cause(err, "parse unix rights")
			return
		}
		if len(fds) != 1 {
			err = E.New("invalid fds count: ", len(fds))
			return
		}
		receivedFd = fds[0]
	})
	err = cmp.Or(controlErr, err)
	if err != nil {
		_, _ = conn.Write([]byte{protectFailed})
		s.logger.ErrorContext(s.ctx, err)
		return
	}
	// SCM_RIGHTS handed us our own descriptor, and protecting it is synchronous.
	defer unix.Close(receivedFd)
	err = s.protector.Protect(receivedFd)
	if err != nil {
		_, _ = conn.Write([]byte{protectFailed})
		s.logger.ErrorContext(s.ctx, err)
		return
	}
	_, _ = conn.Write([]byte{protectSuccess})
}

func (s *Service) Close() error {
	return common.Close(common.PtrOrNil(s.listener))
}
