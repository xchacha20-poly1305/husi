package libcore

import (
	"sync"
	"time"

	"github.com/sagernet/sing-box/log"
)

type servicePauseFields struct {
	pauseAccess sync.Mutex
	pauseTimer  *time.Timer
}

func (b *BoxInstance) Pause() {
	b.pauseAccess.Lock()
	defer b.pauseAccess.Unlock()
	if b.pauseTimer != nil {
		b.pauseTimer.Stop()
	}
	b.pauseTimer = time.AfterFunc(3*time.Second, b.ResetNetwork)
}

func (b *BoxInstance) Wake() {
	b.pauseAccess.Lock()
	defer b.pauseAccess.Unlock()
	if b.pauseTimer != nil {
		b.pauseTimer.Stop()
	}
	b.pauseTimer = time.AfterFunc(3*time.Minute, b.ResetNetwork)
}

func (b *BoxInstance) ResetNetwork() {
	b.Box.Router().ResetNetwork()
	log.Debug("Reset network.")
}
