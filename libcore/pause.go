package libcore

func (b *boxInstance) Pause() {
	if b.pauseManager != nil {
		b.pauseManager.DevicePause()
	}
}

func (b *boxInstance) Wake() {
	if b.pauseManager != nil {
		b.pauseManager.DeviceWake()
	}
}
