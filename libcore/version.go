package libcore

import (
	"runtime"
	"runtime/debug"
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

loop:
	for _, setting := range debugInfo.Settings {
		switch setting.Key {
		case "-tags":
			if setting.Value != "" {
				detailVersionSli = append(detailVersionSli, setting.Value)
				break loop
			}
		}
	}

	detailVersion = strings.Join(detailVersionSli, "\n")
}
