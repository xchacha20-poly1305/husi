package libcore

// PlatformInterface was named "iif".
type PlatformInterface interface {
	AutoDetectInterfaceControl(fd int32) error
	OpenTun() (int, error)
	UseProcFS() bool
	FindConnectionOwner(ipProtocol int32, sourceAddress string, sourcePort int32, destinationAddress string, destinationPort int32) (int32, error)
	PackageNameByUid(uid int32) (string, error)
	UIDByPackageName(packageName string) (int32, error)
	ReadWIFIState() *WIFIState
	StartDefaultInterfaceMonitor(listener InterfaceUpdateListener) error
	CloseDefaultInterfaceMonitor(listener InterfaceUpdateListener) error
	GetInterfaces() (NetworkInterfaceIterator, error)
	UsePlatformInterfaceGetter() bool
	DeviceName() string
	AnchorSSIDs() string

	SelectorCallback(tag string)
	ClashModeCallback(mode string)
}
