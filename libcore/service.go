package libcore

import (
	"errors"
	"io"
	"net"
	"os"
	"sync"
	"syscall"
	"time"

	"libcore/vario"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
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
	s.instance = nil
	if err != nil {
		return err
	}
	return nil
}

func (s *Service) HasInstance() bool {
	s.access.RLock()
	defer s.access.RUnlock()
	return s.instance != nil
}

type ConnectionSubscription struct {
	once     sync.Once
	close    func()
	observer *connectionObserver
	id       int
}

func (c *ConnectionSubscription) Close() error {
	c.once.Do(c.close)
	return nil
}

func (c *ConnectionSubscription) UpdateInterval(intervalMs int32) {
	if c.observer == nil {
		return
	}
	c.observer.updateInterval(c.id, time.Duration(intervalMs)*time.Millisecond)
}

func (s *Service) SubscribeConnections(callback ConnectionEventCallback, intervalMs int32) (*ConnectionSubscription, error) {
	s.access.RLock()
	instance := s.instance
	s.access.RUnlock()
	if instance == nil || instance.connectionObserver == nil {
		return nil, os.ErrNotExist
	}
	observer := instance.connectionObserver
	events := make(chan []ConnectionEvent, 256)
	done := make(chan struct{})
	sink := func(batch []ConnectionEvent) {
		select {
		case events <- batch:
		case <-done:
		default:
		}
	}
	id := observer.register(sink, time.Duration(intervalMs)*time.Millisecond)
	go func() {
		for {
			select {
			case batch := <-events:
				for i := range batch {
					callback.OnConnectionEvent(&batch[i])
				}
			case <-done:
				return
			case <-instance.ctx.Done():
				return
			}
		}
	}()
	return &ConnectionSubscription{
		close: func() {
			observer.unregister(id)
			close(done)
		},
		observer: observer,
		id:       id,
	}, nil
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
	listenPath := apiPath("")
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
	command, err := vario.ReadUint8(conn)
	if err != nil {
		return E.Cause(err, "read command")
	}
	switch command {
	case commandHello:
		err := s.handleHello(conn)
		if err != nil {
			return E.Cause(err, "handle hello")
		}
		return nil
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
			return err
		}
		instance.resetNetwork()
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
	case commandImportDeepLink:
		err := s.handleImportDeepLink(conn)
		if err != nil {
			return E.Cause(err, "handle deep link import")
		}
		return nil
	case commandRunTask:
		err := s.handleRunTask(conn)
		if err != nil {
			return E.Cause(err, "handle task request")
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

func (s *Service) handleHello(conn io.ReadWriter) error {
	err := vario.WriteUint8(conn, resultNoError)
	if err != nil {
		return E.Cause(err, "write result")
	}
	return nil
}

func (s *Service) handleImportDeepLink(conn io.ReadWriter) error {
	deepLinks, err := vario.ReadStringSlice(conn)
	if err != nil {
		return E.Cause(err, "read deep links")
	}
	if len(deepLinks) == 0 || s.platformInterface == nil {
		return nil
	}
	for _, deepLink := range deepLinks {
		s.platformInterface.OnDeepLink(deepLink)
	}
	return nil
}

func (s *Service) handleRunTask(conn io.ReadWriter) error {
	taskID, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read task id")
	}
	if taskID == "" || s.platformInterface == nil {
		return nil
	}
	s.platformInterface.OnTask(taskID)
	return nil
}
