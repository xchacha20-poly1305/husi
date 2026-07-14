//go:build !android

package libcore

// PlatformInterface also named "iif".
type PlatformInterface interface {
	OnGroupSelectedChange(group, old, now string)
	OnDeepLink(deepLink string)
	OnTask(taskID string)
}
