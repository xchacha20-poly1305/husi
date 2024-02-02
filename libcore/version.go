package libcore

import (
	"runtime"
	"runtime/debug"
	"strings"

	C "github.com/sagernet/sing-box/constant"
)

const VERSION = "v1.8.4"

func init() {
	C.Version = VERSION
}

// Version
// Show detail version
//
// Format:
//
//	sing-box: {dun_version}
//	{go_version}@{os}/{arch}
//	{tags}
func Version() string {
	detailVersion := []string{
		"sing-box: " + VERSION,
		runtime.Version() + "@" + runtime.GOOS + "/" + runtime.GOARCH,
	}

	debugInfo, loaded := debug.ReadBuildInfo()
	if loaded {
		for _, setting := range debugInfo.Settings {
			switch setting.Key {
			case "-tags":
				if setting.Value != "" {
					detailVersion = append(detailVersion, setting.Value)
					break
				}
			}
		}
	}

	return strings.Join(detailVersion, "\n")
}
