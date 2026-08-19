//go:build android

package libcore

import (
	"context"
	"fmt"

	C "github.com/sagernet/sing-box/constant"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pluginpool"
	"google.golang.org/protobuf/proto"
)

func (s *Service) SetPluginFatalHandler(handler PluginFatalHandler) {
	s.access.Lock()
	defer s.access.Unlock()
	// Stored only to wire the next pool; the active pool's onFatal closes over
	// a live lookup so the handler can be swapped without restarting plugins.
	s.pluginFatalHandler = handler
}

func (s *Service) StartService(request []byte, pluginWorkingDir string) error {
	req := &husiv1.StartServiceRequest{}
	err := proto.Unmarshal(request, req)
	if err != nil {
		return E.Cause(err, "unmarshal StartServiceRequest")
	}

	s.access.Lock()
	host := s.host
	if host == nil {
		s.access.Unlock()
		return E.New("service not started")
	}
	// Replace any previous pool before starting a new one.
	s.closePluginPoolLocked()
	s.access.Unlock()

	err = host.StartOrReload(context.Background(), req.GetConfig())
	if err != nil {
		return err
	}

	if len(req.GetPlugins()) > 0 {
		pool := pluginpool.NewPluginPool(pluginWorkingDir, s.handlePluginFatal)
		err := pool.StartAll(req.GetPlugins())
		if err != nil {
			_ = pool.Close()
			_ = host.CloseService(C.FatalStopTimeout)
			return E.Cause(err, "start plugins")
		}
		s.access.Lock()
		running := s.host != nil && s.host.HasInstance()
		if running {
			s.pluginPool = pool
		}
		s.access.Unlock()
		if !running {
			// Stopped while the plugins were coming up: nobody would own them.
			_ = pool.Close()
			return E.New("service stopped while starting plugins")
		}
	}
	return nil
}

func (s *Service) PublishServiceEvent(event []byte) error {
	s.access.RLock()
	host := s.host
	s.access.RUnlock()
	if host == nil {
		return E.New("service not started")
	}
	msg := &husiv1.SubscribeServiceEventsResponse{}
	if err := proto.Unmarshal(event, msg); err != nil {
		return E.Cause(err, "unmarshal ServiceEvent")
	}
	host.PublishServiceEvent(msg)
	return nil
}

func (s *Service) Pause() {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.host == nil {
		return
	}
	instance := s.host.Started().Instance()
	if instance == nil {
		return
	}
	instance.PauseManager().DevicePause()
}

func (s *Service) Wake() {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.host == nil {
		return
	}
	instance := s.host.Started().Instance()
	if instance == nil {
		return
	}
	instance.PauseManager().DeviceWake()
}

func (s *Service) ResetNetwork() {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.host == nil {
		return
	}
	instance := s.host.Started().Instance()
	if instance == nil {
		return
	}
	instance.Box().Network().ResetNetwork()
}

func (s *Service) NeedWIFIState() bool {
	s.access.RLock()
	defer s.access.RUnlock()
	if s.host == nil {
		return false
	}
	instance := s.host.Started().Instance()
	if instance == nil {
		return false
	}
	return instance.Box().Network().NeedWIFIState()
}

func (s *Service) handlePluginFatal(err error) {
	if err == nil {
		return
	}
	s.access.RLock()
	handler := s.pluginFatalHandler
	s.access.RUnlock()
	if handler == nil {
		return
	}
	handler.OnPluginFatal(fmt.Sprint(err))
}
