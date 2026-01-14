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
	return s.instance.UrlTest(tag, link, timeout)
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

func (s *Service) Close() error {
	s.access.Lock()
	defer s.access.Unlock()
	_ = s.listener.Close()
	if s.instance != nil {
		_ = s.instance.Close()
		s.instance = nil
	}
	return nil
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
	case commandNewInstance:
		config, err := varbin.ReadValue[string](conn, binary.BigEndian)
		if err != nil {
			return E.Cause(err, "read config")
		}
		s.access.Lock()
		defer s.access.Unlock()
		if s.instance != nil {
			return os.ErrExist
		}
		return s.newInstance(config)
	case commandStart:
		s.access.Lock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.Unlock()
			return err
		}
		defer s.access.Unlock()
		return instance.Start()
	case commandStop:
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
	case commandQueryConnections:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
			return err
		}
		err = s.handleQueryConnections(conn, instance)
		s.access.RUnlock()
		if err != nil {
			return E.Cause(err, "handle query connections")
		}
		return nil
	case commandCloseConnection:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
			return err
		}
		err = s.handleCloseConnection(conn, instance)
		s.access.RUnlock()
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
		if err != nil {
			s.access.RUnlock()
			return err
		}
		err = s.handleQueryClashModes(conn, instance)
		s.access.RUnlock()
		if err != nil {
			return E.Cause(err, "handle query clash modes")
		}
		return nil
	case commandQuerySelectedClashMode:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
			return err
		}
		err = s.handleQuerySelectedClashMode(conn, instance)
		s.access.RUnlock()
		if err != nil {
			return E.Cause(err, "handle query selected clash mode")
		}
		return nil
	case commandSetClashMode:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
			return err
		}
		err = s.handleSetClashMode(conn, instance)
		s.access.RUnlock()
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
		if err != nil {
			s.access.RUnlock()
			return err
		}
		err = s.handleGroupTest(conn, instance)
		s.access.RUnlock()
		if err != nil {
			return E.Cause(err, "handle group test")
		}
		return nil
	case commandSelectOutbound:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
		}
		err = s.handleSelectOutbound(conn, instance)
		s.access.RUnlock()
		if err != nil {
			return E.Cause(err, "handle select outbound")
		}
		return nil
	case commandQueryProxySets:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
		}
		err = s.handleQueryProxySets(conn, instance)
		s.access.RUnlock()
		if err != nil {
			return E.Cause(err, "handle query proxy sets")
		}
		return nil
	case commandResetNetwork:
		ResetAllConnections()
		return nil
	case commandQueryLogs:
		err := s.handleQueryLogs(conn)
		if err != nil {
			return E.Cause(err, "handle query logs")
		}
		return nil
	case commandClearLog:
		LogClear()
		return nil
	case commandPause:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
			return err
		}
		instance.Pause()
		s.access.RUnlock()
		return nil
	case commandWake:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
			return err
		}
		instance.Wake()
		s.access.RUnlock()
		return nil
	case commandNeedWIFIState:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
			return err
		}
		needWIFI := instance.NeedWIFIState()
		s.access.RUnlock()
		err = varbin.Write(conn, binary.BigEndian, needWIFI)
		if err != nil {
			return E.Cause(err, "write need wifi state")
		}
		return nil
	case commandQueryStats:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
			return err
		}
		err = s.handleQueryStats(conn, instance)
		s.access.RUnlock()
		if err != nil {
			return E.Cause(err, "handle query stats")
		}
		return nil
	case commandInitializeProxySet:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
			return err
		}
		instance.InitializeProxySet()
		s.access.RUnlock()
		return nil
	case commandUrlTest:
		s.access.RLock()
		instance, err := s.requireInstance()
		if err != nil {
			s.access.RUnlock()
			return err
		}
		err = s.handleUrlTest(conn, instance)
		s.access.RUnlock()
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

func (s *Service) handleUrlTest(conn io.ReadWriter, instance *boxInstance) error {
	tag, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read tag")
	}
	link, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read link")
	}
	timeout, err := varbin.ReadValue[int32](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read timeout")
	}
	latency, err := instance.UrlTest(tag, link, timeout)
	if err != nil {
		_ = varbin.Write(conn, binary.BigEndian, resultCommonError)
		_ = varbin.Write(conn, binary.BigEndian, err.Error())
		return nil
	}
	err = varbin.Write(conn, binary.BigEndian, resultNoError)
	if err != nil {
		return E.Cause(err, "write result")
	}
	err = varbin.Write(conn, binary.BigEndian, latency)
	if err != nil {
		return E.Cause(err, "write latency")
	}
	return nil
}
