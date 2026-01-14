package libcore

import (
	"github.com/sagernet/sing-box/log"
)

func (b *boxInstance) Pause() {
	b.pauseManager.DevicePause()
}

func (b *boxInstance) Wake() {
	b.pauseManager.DeviceWake()
}

func (b *boxInstance) ResetNetwork() {
	b.Box.Router().ResetNetwork()
	log.Debug("Reset network.")
}
