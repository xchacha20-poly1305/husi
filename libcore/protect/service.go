package protect

import (
	"cmp"
	"context"
	"net"
	"os"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"

	"golang.org/x/sys/unix"
)

const (
	netUnix = "unix"

	ProtectFailed byte = iota
	ProtectSuccess
)

// Service is a service that accept unix connection
// and invoke do with the received fd, which should be protected.
type Service struct {
	logger   logger.ContextLogger
	ctx      context.Context
	listener net.Listener
	do       func(int) error
	done     chan struct{}
}

func New(ctx context.Context, ctxLogger logger.ContextLogger, path string, do func(fd int) error) (*Service, error) {
	if path == "" {
		return nil, E.New("missing path")
	}
	if do == nil {
		return nil, E.New("missing protect implementation")
	}
	_ = os.Remove(path)
	listener, err := net.ListenUnix(netUnix, &net.UnixAddr{
		Name: path,
		Net:  netUnix,
	})
	if err != nil {
		return nil, E.Cause(err, "listen unix")
	}
	_ = os.Chmod(path, os.ModePerm)
	return &Service{
		logger:   ctxLogger,
		ctx:      ctx,
		listener: listener,
		do:       do,
		done:     make(chan struct{}),
	}, nil
}

func (p *Service) Start() error {
	go p.loop()
	return nil
}

func (p *Service) loop() {
	p.logger.DebugContext(p.ctx, "start loop")
	for {
		select {
		case <-p.ctx.Done():
			return
		case <-p.done:
			return
		default:
		}
		conn, err := p.listener.Accept()
		if err != nil {
			p.handleError(err)
			return
		}
		go p.handle(conn.(*net.UnixConn))
	}
}

func (p *Service) handle(conn *net.UnixConn) {
	defer conn.Close()
	rawConn, err := conn.SyscallConn()
	if err != nil {
		_, _ = conn.Write([]byte{ProtectFailed})
		p.logger.DebugContext(p.ctx, "protect")
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
		_, _ = conn.Write([]byte{ProtectFailed})
		p.handleError(err)
		return
	}
	err = p.do(receivedFd)
	if err != nil {
		_, _ = conn.Write([]byte{ProtectFailed})
		p.handleError(err)
		return
	}
	_, _ = conn.Write([]byte{ProtectSuccess})
}

func (p *Service) handleError(err error) {
	if E.IsClosedOrCanceled(err) {
		return
	}
	p.logger.DebugContext(p.ctx, err)
}

func (p *Service) Close() error {
	if p.done == nil {
		return os.ErrInvalid
	}
	select {
	case <-p.done:
		return net.ErrClosed
	default:
		close(p.done)
	}
	err := common.Close(p.listener)
	if err != nil {
		return err
	}
	p.logger.InfoContext(p.ctx, "protect service closed")
	return nil
}
