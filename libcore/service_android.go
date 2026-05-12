//go:build android

package libcore

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
		s.instance.resetNetwork()
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
