package libcore

import (
	"runtime"
	"runtime/debug"
	"strings"

	C "github.com/sagernet/sing-box/constant"
)

const SingBoxPath = "github.com/sagernet/sing-box"

func init() {
	buildInfo, _ := debug.ReadBuildInfo()
	for _, dep := range buildInfo.Deps {
		switch dep.Path {
		case SingBoxPath:
			C.Version = dep.Version
			return
		}
	}
}

// VersionBox returns sing-box version
func VersionBox() string {
	return C.Version
}

// Version
// Show detail version
// Format:
//
//	sing-box: {dun_version}
//	{go_version}@{os}/{arch}
//	{tags}
func Version() string {
	detailVersion := []string{
		"sing-box: " + C.Version,
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
