package libcore

import (
	"github.com/sagernet/sing-box/log"
)

func (b *BoxInstance) Pause() {
	b.pauseManager.DevicePause()
}

func (b *BoxInstance) Wake() {
	b.pauseManager.DeviceWake()
}

func (b *BoxInstance) ResetNetwork() {
	b.Box.Router().ResetNetwork()
	log.Debug("Reset network.")
}
