//go:build android

package libcore

import (
	"os"
	"time"

	"github.com/sagernet/sing-box/log"
)

const stuckExitGrace = 500 * time.Millisecond

func (s *Service) hostStuck(err error) {
	log.Error(err, ": ending the service process so a working one can replace it")
	time.Sleep(stuckExitGrace)
	os.Exit(0)
}
