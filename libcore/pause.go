package libcore

func (b *boxInstance) Pause() {
	b.pauseManager.DevicePause()
}

func (b *boxInstance) Wake() {
	b.pauseManager.DeviceWake()
}
