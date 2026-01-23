package libcore

import (
	"errors"
	"io"
	"net"
	"os"
	"sync"
	"syscall"
	"time"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/binary"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/varbin"
)

type Service struct {
	access            sync.RWMutex
	platformInterface PlatformInterface
	instance          *boxInstance
	listener          *net.UnixListener
}

func NewService(platformInterface PlatformInterface) *Service {
	return &Service{
		platformInterface: platformInterface,
	}
}

func (s *Service) NewInstance(config string) error {
	s.access.Lock()
	defer s.access.Unlock()
	return s.newInstance(config)
}

func (s *Service) StartInstance() error {
	s.access.Lock()
	defer s.access.Unlock()
	if s.instance == nil {
		return E.New("instance not created")
	}
	return s.instance.Start()
}

func (s *Service) StopInstance() error {
	s.access.Lock()
	defer s.access.Unlock()
	if s.instance == nil {
		return nil
	}
	err := s.instance.Close()
	if err != nil {
		return err
	}
	s.instance = nil
	return nil
}

func (s *Service) HasInstance() bool {
	s.access.RLock()
	defer s.access.RUnlock()
	return s.instance != nil
}

func (s *Service) Pause() {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.instance != nil {
		s.instance.Pause()
	}
}

func (s *Service) Wake() {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.instance != nil {
		s.instance.Wake()
	}
}

func (s *Service) ResetNetwork() {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.instance != nil {
		s.instance.ResetNetwork()
	}
}

func (s *Service) NeedWIFIState() bool {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.instance == nil {
		return false
	}
	return s.instance.NeedWIFIState()
}

func (s *Service) QueryStats(tag string, isUpload bool) int64 {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.instance == nil {
		return 0
	}
	return s.instance.QueryStats(tag, isUpload)
}

func (s *Service) InitializeProxySet() {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.instance != nil {
		s.instance.InitializeProxySet()
	}
}

func (s *Service) UrlTest(tag, link string, timeout int32) (int32, error) {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.instance == nil {
		return -1, E.New("instance not created")
	}
	return s.instance.urlTest(tag, link, timeout)
}

func (s *Service) Start() error {
	if s.listener != nil {
		return os.ErrExist
	}
	var (
		listener *net.UnixListener
		err      error
	)
	listenPath := apiPath()
	_ = os.Remove(listenPath)
	// Copied from libbox, idk why.
	for range 30 {
		listener, err = net.ListenUnix("unix", &net.UnixAddr{
			Name: listenPath,
			Net:  "unix",
		})
		if err == nil {
			break
		}
		if !errors.Is(err, syscall.EROFS) {
			break
		}
		time.Sleep(1 * time.Second)
	}
	if err != nil {
		return E.Cause(err, "listen command server")
	}
	s.listener = listener
	go s.loopHandle(s.listener)
	return nil
}

func (s *Service) Close() (err error) {
	s.access.Lock()
	defer s.access.Unlock()
	err = common.Close(
		common.PtrOrNil(s.listener),
		common.PtrOrNil(s.instance),
	)
	s.listener = nil
	s.instance = nil
	return
}

func (s *Service) loopHandle(listener net.Listener) {
	for {
		conn, err := listener.Accept()
		if err != nil {
			if !E.IsClosed(err) {
				log.Warn("stop API service because ", err)
			}
			return
		}
		go func() {
			defer conn.Close()
			hasSucceed := false
			for {
				err := s.handleRequest(conn)
				if err != nil {
					if !hasSucceed && !E.IsClosed(err) {
						log.Error("handle request: ", err)
					}
					break
				}
				hasSucceed = true
			}
		}()
	}
}

func (s *Service) handleRequest(conn net.Conn) error {
	command, err := varbin.ReadValue[uint8](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read command")
	}
	switch command {
	case commandQueryConnections:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			return err
		}
		err = s.handleQueryConnections(conn, instance)
		if err != nil {
			return E.Cause(err, "handle query connections")
		}
		return nil
	case commandSubscribeConnections:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			return err
		}
		err = s.handleSubscribeConnections(conn, instance)
		if err != nil {
			return E.Cause(err, "handle subscribe connections")
		}
		return nil
	case commandCloseConnection:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			return err
		}
		err = s.handleCloseConnection(conn, instance)
		if err != nil {
			return E.Cause(err, "handle close connection")
		}
		return nil
	case commandQueryMemory:
		err := s.handleQueryMemory(conn)
		if err != nil {
			return E.Cause(err, "handle query memory")
		}
		return nil
	case commandQueryGoroutines:
		err := s.handleQueryGoroutines(conn)
		if err != nil {
			return E.Cause(err, "handle query goroutines")
		}
		return nil
	case commandQueryClashModes:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			return err
		}
		err = s.handleQueryClashModes(conn, instance)
		if err != nil {
			return E.Cause(err, "handle query clash modes")
		}
		return nil
	case commandSubscribeClashMode:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			return err
		}
		err = s.handleSubscribeClashMode(conn, instance)
		if err != nil {
			return E.Cause(err, "handle query selected clash mode")
		}
		return nil
	case commandSetClashMode:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			return err
		}
		err = s.handleSetClashMode(conn, instance)
		if err != nil {
			return E.Cause(err, "handle set clash mode")
		}
		return nil
	case commandNewInstanceURLTest:
		err := s.handleNewInstanceURLTest(conn)
		// No need to wrap
		return err
	case commandGroupURLTest:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			return err
		}
		err = s.handleGroupTest(conn, instance)
		if err != nil {
			return E.Cause(err, "handle group test")
		}
		return nil
	case commandSelectOutbound:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			return err
		}
		err = s.handleSelectOutbound(conn, instance)
		if err != nil {
			return E.Cause(err, "handle select outbound")
		}
		return nil
	case commandQueryProxySets:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			return err
		}
		err = s.handleQueryProxySets(conn, instance)
		if err != nil {
			return E.Cause(err, "handle query proxy sets")
		}
		return nil
	case commandResetNetwork:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			ResetAllConnections()
		} else {
			instance.ResetNetwork()
		}
		return nil
	case commandSubscribeLogs:
		err := s.handleSubscribeLogs(conn)
		if err != nil {
			return E.Cause(err, "handle query logs")
		}
		return nil
	case commandClearLog:
		LogClear()
		return nil
	case commandUrlTest:
		s.access.RLock()
		instance, err := s.requireInstance()
		s.access.RUnlock()
		if err != nil {
			return err
		}
		err = s.handleUrlTest(conn, instance)
		if err != nil {
			return E.Cause(err, "handle url test")
		}
		return nil
	default:
		return E.New("unknown command: ", command)
	}
}

func (s *Service) newInstance(config string) error {
	if s.platformInterface == nil {
		return E.Cause(os.ErrInvalid, "platform interface is nil")
	}
	if s.instance != nil {
		return E.Cause(os.ErrExist, "instance exists")
	}
	instance, err := newBoxInstance(config, s.platformInterface, false)
	if err != nil {
		return err
	}
	s.instance = instance
	return nil
}

func (s *Service) requireInstance() (*boxInstance, error) {
	if s.instance == nil {
		return nil, E.New("box instance not created")
	}
	return s.instance, nil
}

func (s *Service) handleQueryStats(conn io.ReadWriter, instance *boxInstance) error {
	tag, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read tag")
	}
	isUpload, err := varbin.ReadValue[bool](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read isUpload")
	}
	stats := instance.QueryStats(tag, isUpload)
	err = varbin.Write(conn, binary.BigEndian, stats)
	if err != nil {
		return E.Cause(err, "write stats")
	}
	return nil
}
