//go:build android

package libcore

import (
	"context"
	"fmt"

	husiv1 "libcore/pb/husi/v1"
	"libcore/pluginpool"

	E "github.com/sagernet/sing/common/exceptions"

	"google.golang.org/protobuf/proto"
)

func (s *Service) SetPluginFatalHandler(handler PluginFatalHandler) {
	s.access.Lock()
	defer s.access.Unlock()
	// Stored only to wire the next pool; the active pool's onFatal closes over
	// a live lookup so the handler can be swapped without restarting plugins.
	s.pluginFatalHandler = handler
}

// StartService starts the instance described by a serialized
// husi.v1.StartServiceRequest, spawning the plugin processes it carries into
// pluginWorkingDir. Android is the only caller: the desktop hostings go
// through daemonhost's DaemonService instead.
func (s *Service) StartService(request []byte, pluginWorkingDir string) error {
	req := &husiv1.StartServiceRequest{}
	if err := proto.Unmarshal(request, req); err != nil {
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

	var pool *pluginpool.PluginPool
	if len(req.GetPlugins()) > 0 {
		pool = pluginpool.NewPluginPool(pluginWorkingDir, s.handlePluginFatal)
		if err := pool.StartAll(req.GetPlugins()); err != nil {
			_ = pool.Close()
			s.access.Unlock()
			return E.Cause(err, "start plugins")
		}
		s.pluginPool = pool
	}
	s.access.Unlock()

	if err := host.StartOrReload(context.Background(), req.GetConfig()); err != nil {
		s.access.Lock()
		s.closePluginPoolLocked()
		s.access.Unlock()
		return err
	}
	return nil
}

// PublishServiceEvent broadcasts a serialized husi.v1.SubscribeServiceEventsResponse
// to every SubscribeServiceEvents subscriber. The ":bg" process is the publisher;
// the UI process is the subscriber.
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
