package libcore

import (
	"runtime"
	"runtime/debug"

	"github.com/sagernet/sing-box/common/badversion"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing/common"
)

// VersionBox returns sing-box version
func VersionBox() string {
	return C.Version
}

// BuildEnvironment returns Go version and build tags.
func BuildEnvironment() string {
	buildEnvironment := runtime.Version() + "@" + runtime.GOOS + "/" + runtime.GOARCH + "\n"
	debugInfo, _ := debug.ReadBuildInfo()
	buildEnvironment += common.Find(debugInfo.Settings, func(it debug.BuildSetting) bool {
		return it.Key == "-tags"
	}).Value
	return buildEnvironment
}

func IsPreRelease(versionName string) bool {
	return badversion.Parse(versionName).PreReleaseIdentifier != ""
}
