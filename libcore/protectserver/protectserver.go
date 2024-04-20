package protectserver

import (
	"io"
	"log"
	"net"
	"os"
	"reflect"

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
	// recvmsg
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

// FdFromConn get net.Conn's file descriptor.
func FdFromConn(conn net.Conn) int {
	v := reflect.ValueOf(conn)
	netFD := reflect.Indirect(reflect.Indirect(v).FieldByName("fd"))
	pfd := reflect.Indirect(netFD.FieldByName("pfd"))
	fd := int(pfd.FieldByName("Sysfd").Int())
	return fd
}

// ServerProtect listen at path, and use protectCtl to implement VPN protect.
func ServerProtect(path string, protectCtl Protect) io.Closer {
	if debug.Enabled {
		log.Println("ServerProtect", path)
	}

	if protectCtl == nil {
		if debug.Enabled {
			log.Println("Not provide protectCtl")
		}
		return nil
	}

	_ = os.Remove(path)
	l, err := net.ListenUnix("unix", &net.UnixAddr{Name: path, Net: "unix"})
	if err != nil {
		log.Println(err)
		return nil
	}
	_ = os.Chmod(path, 0o777)

	go func(ctl Protect) {
		for {
			conn, err := l.Accept()
			if err != nil {
				if debug.Enabled {
					log.Println("protect server accept:", err)
				}
				return
			}

			go func() {
				defer conn.Close()
				socketFd := FdFromConn(conn)
				if socketFd < 0 {
					if debug.Enabled {
						log.Println("didn't find socketFd")
					}
					return
				}

				fd, err := getOneFd(socketFd)
				if err != nil {
					if debug.Enabled {
						log.Println("protect server getOneFd:", err)
					}
					return
				}
				defer unix.Close(fd)

				err = ctl(fd)
				if err != nil {
					_, _ = conn.Write([]byte{ProtectFailed})
					return
				}
				_, _ = conn.Write([]byte{ProtectSuccess})
			}()
		}
	}(protectCtl)

	return l
}
