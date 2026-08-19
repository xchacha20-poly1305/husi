//go:build !android

package libcore

import "github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"

func NewService(version string) *Service {
	return &Service{
		version: version,
	}
}

type AppHandler interface {
	OnShowWindow()
	OnDispatchDeepLinks(links StringIterator)
	OnRunTask(taskID string)
}

type appHandlerAdapter struct {
	handler AppHandler
}

func (a appHandlerAdapter) OnShowWindow() {
	a.handler.OnShowWindow()
}

func (a appHandlerAdapter) OnDispatchDeepLinks(links []string) {
	a.handler.OnDispatchDeepLinks(newIterator(links))
}

func (a appHandlerAdapter) OnRunTask(taskID string) {
	a.handler.OnRunTask(taskID)
}

// SetAppHandler registers the desktop AppService handler. Call before Start.
func (s *Service) SetAppHandler(handler AppHandler) {
	var adapted coresvc.AppHandler
	if handler != nil {
		adapted = appHandlerAdapter{handler: handler}
	}
	s.access.Lock()
	defer s.access.Unlock()
	s.appHandler = adapted
	if s.host != nil {
		s.host.SetAppHandler(adapted)
	}
}
