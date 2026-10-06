package coresvc

import (
	"runtime"
	"runtime/debug"
)

func BuildEnvironment() string {
	buildEnvironment := runtime.Version() + "@" + runtime.GOOS + "/" + runtime.GOARCH + "\n"
	buildInfo, ok := debug.ReadBuildInfo()
	if !ok {
		return buildEnvironment
	}
	for _, setting := range buildInfo.Settings {
		if setting.Key == "-tags" {
			return buildEnvironment + setting.Value
		}
	}
	return buildEnvironment
}
