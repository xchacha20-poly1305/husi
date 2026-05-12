//go:build !android

package libcore

// PlatformInterface also named "iif".
type PlatformInterface interface {
	DeviceName() string
	AnchorSSID() string
	OnGroupSelectedChange(group, old, now string)
	OnDeepLink(deepLink string)
	OnTask(taskID string)
}
