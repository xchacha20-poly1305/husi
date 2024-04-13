package libcore

import (
	"sync"
	"time"
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

	b.pauseTimer = time.AfterFunc(time.Minute, b.pause)
}

func (b *BoxInstance) pause() {
	b.pauseAccess.Lock()
	defer b.pauseAccess.Unlock()

	b.pauseManager.DevicePause()
	_ = b.Box.Router().ResetNetwork()
	b.pauseTimer = nil
}

func (b *BoxInstance) Wake() {
	_ = b.Box.Router().ResetNetwork()
	b.pauseAccess.Lock()
	defer b.pauseAccess.Unlock()

	if b.pauseTimer != nil {
		b.pauseTimer.Stop()
		b.pauseTimer = nil
		return
	}

	b.pauseManager.DeviceWake()
}
