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

var (
	detailVersion     string
	detailVersionOnce sync.Once
)

// Version
// Show detail version
// Format:
//
//	sing-box: {dun_version}
//	{go_version}@{os}/{arch}
//	{tags}
func Version() string {
	detailVersionOnce.Do(loadDetailVersion)
	return detailVersion
}

func loadDetailVersion() {
	detailVersionSli := []string{
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
		detailVersionSli = append(detailVersionSli, debugInfo.Settings[tagsSettingIndex].Value)
	}

	detailVersion = strings.Join(detailVersionSli, "\n")
}
