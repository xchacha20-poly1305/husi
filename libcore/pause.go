package libcore

import (
	"github.com/sagernet/sing-box/log"
)

func (b *BoxInstance) Pause() {
	b.pauseManager.DevicePause()
	b.Router().ResetNetwork()
}

func (b *BoxInstance) Wake() {
	b.pauseManager.DeviceWake()
	b.Router().ResetNetwork()
}

func (b *BoxInstance) ResetNetwork() {
	b.Box.Router().ResetNetwork()
	log.Debug("Reset network.")
}
