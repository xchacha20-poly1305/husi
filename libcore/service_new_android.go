//go:build android

package libcore

func NewService(version string, platformInterface PlatformInterface) *Service {
	return &Service{
		version:           version,
		platformInterface: platformInterface,
	}
}
