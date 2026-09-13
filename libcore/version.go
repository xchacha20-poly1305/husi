package libcore

import (
	"runtime"
	"runtime/debug"
	"strings"

	"github.com/sagernet/sing-box/common/badversion"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing/common"

	"golang.org/x/mod/semver"
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

func CompareSemver(left string, right string) bool {
	normalizedLeft := normalizeSemver(left)
	if !semver.IsValid(normalizedLeft) {
		return false
	}
	normalizedRight := normalizeSemver(right)
	if !semver.IsValid(normalizedRight) {
		return false
	}
	return badversion.Parse(normalizedLeft).GreaterThan(badversion.Parse(normalizedRight))
}

func normalizeSemver(version string) string {
	trimmedVersion := strings.TrimSpace(version)
	if strings.HasPrefix(trimmedVersion, "v") {
		return trimmedVersion
	}
	return "v" + trimmedVersion
}
