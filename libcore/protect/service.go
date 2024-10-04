package protect

import (
	"context"
	"net"
	"os"

	"github.com/sagernet/sing-box/adapter"
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

var (
	_ adapter.Service = (*Protect)(nil)
	_ E.Handler       = (*Protect)(nil)
)

type Protect struct {
	logger   logger.ContextLogger
	ctx      context.Context
	listener net.Listener
	do       func(int) error
	done     chan struct{}
}

func New(ctx context.Context, ctxLogger logger.ContextLogger, path string, do func(fd int) error) (*Protect, error) {
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
	return &Protect{
		logger:   ctxLogger,
		ctx:      ctx,
		listener: listener,
		do:       do,
		done:     make(chan struct{}),
	}, nil
}

func (p *Protect) Start() error {
	go p.loop()
	return nil
}

func (p *Protect) loop() {
	stop := context.AfterFunc(p.ctx, func() {
		_ = p.Close()
	})
	p.logger.DebugContext(p.ctx, "Protect: start loop")
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
			stop()
			p.NewError(p.ctx, err)
			return
		}
		go p.handle(conn.(*net.UnixConn))
	}
}

func (p *Protect) handle(conn *net.UnixConn) {
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
	if controlErr != nil {
		_, _ = conn.Write([]byte{ProtectFailed})
		p.NewError(p.ctx, controlErr)
		return
	}
	if err != nil {
		_, _ = conn.Write([]byte{ProtectFailed})
		p.NewError(p.ctx, err)
		return
	}
	err = p.do(receivedFd)
	if err != nil {
		_, _ = conn.Write([]byte{ProtectFailed})
		p.NewError(p.ctx, err)
		return
	}
	_, _ = conn.Write([]byte{ProtectSuccess})
}

func (p *Protect) NewError(ctx context.Context, err error) {
	if E.IsClosedOrCanceled(err) {
		return
	}
	p.logger.DebugContext(ctx, "protect server: ", err)
}

func (p *Protect) Close() error {
	select {
	case <-p.done:
		return net.ErrClosed
	default:
		close(p.done)
	}
	return common.Close(p.listener)
}
