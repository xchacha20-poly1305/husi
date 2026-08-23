//go:build !android

package libcore

import "github.com/sagernet/sing-box/log"

func (s *Service) hostStuck(err error) {
	log.Error(err, ": the core host can no longer serve")
}
