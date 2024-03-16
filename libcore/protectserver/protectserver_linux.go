package protectserver

import (
	"io"
	"log"
	"net"
	"os"
	"syscall"

	"github.com/sagernet/sing/common/debug"
	E "github.com/sagernet/sing/common/exceptions"
)

func getOneFd(socket int) (int, error) {
	// recvmsg
	buf := make([]byte, syscall.CmsgSpace(4))
	_, _, _, _, err := syscall.Recvmsg(socket, nil, buf, 0)
	if err != nil {
		return 0, err
	}

	// parse control msgs
	var msgs []syscall.SocketControlMessage
	msgs, _ = syscall.ParseSocketControlMessage(buf)

	if len(msgs) != 1 {
		return 0, E.New("invalid msgs count: ", len(msgs))
	}

	var fds []int
	fds, _ = syscall.ParseUnixRights(&msgs[0])
	if len(fds) != 1 {
		return 0, E.New("invalid fds count: ", len(fds))
	}
	return fds[0], nil
}

type fileGetter interface {
	File() (*os.File, error)
}

// GetFdFromConn get net.Conn's file descriptor.
func GetFdFromConn(conn net.Conn) int {
	if f, ok := conn.(fileGetter); ok {
		file, err := f.File()
		if err == nil {
			return int(file.Fd())
		}
	}

	return -1
}

func ServeProtect(path string, fwmark int, protectCtl func(fd int)) io.Closer {
	if debug.Enabled {
		log.Println("ServeProtect", path, fwmark)
	}

	_ = os.Remove(path)
	l, err := net.ListenUnix("unix", &net.UnixAddr{Name: path, Net: "unix"})
	if err != nil {
		log.Fatal(err)
	}
	_ = os.Chmod(path, 0777)

	go func(ctl func(fd int)) {
		for {
			conn, err := l.Accept()
			if err != nil {
				if debug.Enabled {
					log.Println("protect server accept:", err)
				}
				return
			}

			go func() {
				socket := GetFdFromConn(conn)
				defer conn.Close()
				if socket < 0 {
					if debug.Enabled {
						log.Println("didn't find socket fd")
					}
					return
				}

				fd, err := getOneFd(socket)
				if err != nil {
					if debug.Enabled {
						log.Println("protect server getOneFd:", err)
					}
					return
				}
				defer syscall.Close(fd)

				if ctl == nil {
					// linux
					err := syscall.SetsockoptInt(fd, syscall.SOL_SOCKET, syscall.SO_MARK, fwmark)
					if debug.Enabled && err != nil {
						log.Println("protect server syscall.SetsockoptInt:", err)
					}
				} else {
					// android
					ctl(fd)
				}

				if err == nil {
					_, _ = conn.Write([]byte{1})
				} else {
					_, _ = conn.Write([]byte{0})
				}
			}()
		}
	}(protectCtl)

	return l
}
