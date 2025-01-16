package libcore

// PlatformInterface also named "iif".
type PlatformInterface interface {
	AutoDetectInterfaceControl(fd int32) error
	OpenTun() (int, error)
	UseProcFS() bool
	FindConnectionOwner(ipProtocol int32, sourceAddress string, sourcePort int32, destinationAddress string, destinationPort int32) (int32, error)
	PackageNameByUid(uid int32) (string, error)
	ReadWIFIState() WIFIState
	StartDefaultInterfaceMonitor(listener InterfaceUpdateListener) error
	CloseDefaultInterfaceMonitor(listener InterfaceUpdateListener) error
	GetInterfaces() (NetworkInterfaceIterator, error)
	DeviceName() string
	AnchorSSID() string
}
