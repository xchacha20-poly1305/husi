package protect

import (
	"io"
	"net"
	"os"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common/debug"
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/unix"
)

const (
	ProtectFailed byte = iota
	ProtectSuccess
)

type Protect func(fd int) error

func getOneFd(socketFd int) (int, error) {
	buf := make([]byte, unix.CmsgSpace(4))
	_, _, _, _, err := unix.Recvmsg(socketFd, nil, buf, 0)
	if err != nil {
		return 0, err
	}

	// parse control msgs
	msgs, _ := unix.ParseSocketControlMessage(buf)

	if len(msgs) != 1 {
		return 0, E.New("invalid msgs count: ", len(msgs))
	}

	fds, _ := unix.ParseUnixRights(&msgs[0])
	if len(fds) != 1 {
		return 0, E.New("invalid fds count: ", len(fds))
	}
	return fds[0], nil
}

// ServerProtect listen at path, and use protectCtl to implement VPN protect.
func ServerProtect(path string, protectCtl Protect) io.Closer {
	if debug.Enabled {
		log.Info("ServerProtect", path)
	}

	if protectCtl == nil {
		if debug.Enabled {
			log.Error("Not provide protectCtl")
		}
		return nil
	}

	_ = os.Remove(path)
	l, err := net.ListenUnix("unix", &net.UnixAddr{Name: path, Net: "unix"})
	if err != nil {
		log.Error(err)
		return nil
	}
	_ = os.Chmod(path, 0o777)

	go func(ctl Protect) {
		for {
			conn, err := l.Accept()
			if err != nil {
				if debug.Enabled {
					log.Error(E.Cause(err, "protect server accept"))
				}
				return
			}

			// handle
			go func(conn *net.UnixConn) {
				defer conn.Close()

				rawConn, err := conn.SyscallConn()
				if err != nil {
					return
				}

				// Get the fd of conn and receive fd.
				var (
					connFd int
					recvFd int
				)
				err = rawConn.Control(func(fd uintptr) {
					connFd = int(fd)
					recvFd, err = getOneFd(connFd)
				})
				defer unix.Close(connFd)

				err = ctl(recvFd)
				if err != nil {
					_, _ = conn.Write([]byte{ProtectFailed})
					if debug.Enabled {
						log.Error("Failed to control fd")
					}
					return
				}
				_, _ = conn.Write([]byte{ProtectSuccess})
			}(conn.(*net.UnixConn))
		}
	}(protectCtl)

	return l
}
