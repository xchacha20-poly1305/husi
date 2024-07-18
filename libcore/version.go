package libcore

import (
	"cmp"
	"runtime"
	"runtime/debug"
	"slices"
	"strings"
	"sync"

	C "github.com/sagernet/sing-box/constant"
)

// VersionBox returns sing-box version
func VersionBox() string {
	return C.Version
}

// Version shows detail version. Format:
//
//	sing-box: {C.Version}
//	{go_version}@{os}/{arch}
//	{tags}
func Version() string {
	return detailVersion()
}

var detailVersion = sync.OnceValue(loadDetailVersion)

func loadDetailVersion() string {
	builder := []string{
		"sing-box: " + C.Version,
		runtime.Version() + "@" + runtime.GOOS + "/" + runtime.GOARCH,
	}

	debugInfo, _ := debug.ReadBuildInfo()

	tagsSettingIndex, found := slices.BinarySearchFunc(
		debugInfo.Settings,
		debug.BuildSetting{Key: "-tags"},
		func(a, b debug.BuildSetting) int {
			return cmp.Compare(a.Key, b.Key)
		},
	)
	if found {
		builder = append(builder, debugInfo.Settings[tagsSettingIndex].Value)
	}

	return strings.Join(builder, "\n")
}
