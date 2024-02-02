package protectserver

import (
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"reflect"
	"syscall"

	"github.com/sagernet/sing/common/debug"
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
		return 0, fmt.Errorf("invaild msgs count: %d", len(msgs))
	}

	var fds []int
	fds, _ = syscall.ParseUnixRights(&msgs[0])
	if len(fds) != 1 {
		return 0, fmt.Errorf("invaild fds count: %d", len(fds))
	}
	return fds[0], nil
}

// GetFdFromConn get net.Conn's file descriptor.
func GetFdFromConn(l net.Conn) int {
	v := reflect.ValueOf(l)
	netFD := reflect.Indirect(reflect.Indirect(v).FieldByName("fd"))
	pfd := reflect.Indirect(netFD.FieldByName("pfd"))
	fd := int(pfd.FieldByName("Sysfd").Int())
	return fd
}

func ServeProtect(path string, fwmark int, protectCtl func(fd int)) io.Closer {
	if debug.Enabled {
		log.Println("ServeProtect", path, fwmark)
	}

	os.Remove(path)
	l, err := net.ListenUnix("unix", &net.UnixAddr{Name: path, Net: "unix"})
	if err != nil {
		log.Fatal(err)
	}
	os.Chmod(path, 0777)

	go func(ctl func(fd int)) {
		for {
			c, err := l.Accept()
			if err != nil {
				if debug.Enabled {
					log.Println("protect server accept:", err)
				}
				return
			}

			go func() {
				socket := GetFdFromConn(c)
				defer c.Close()

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
					if err := syscall.SetsockoptInt(fd, syscall.SOL_SOCKET, syscall.SO_MARK, fwmark); err != nil {
						log.Println("protect server syscall.SetsockoptInt:", err)
					}
				} else {
					// android
					ctl(fd)
				}

				if err == nil {
					c.Write([]byte{1})
				} else {
					c.Write([]byte{0})
				}
			}()
		}
	}(protectCtl)

	return l
}
