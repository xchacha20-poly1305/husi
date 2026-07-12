package libcore

import (
	"path/filepath"
)

const Socket = "api.sock"

const (
	commandHello uint8 = iota
	commandQueryConnections
	commandSubscribeConnections
	commandCloseConnection
	commandQueryMemory
	commandQueryGoroutines
	commandQueryClashModes
	commandSubscribeClashMode
	commandSetClashMode
	commandUrlTest
	commandNewInstanceURLTest
	commandSelectOutbound
	commandQueryProxySets
	commandResetNetwork
	commandClearLog
	commandSubscribeLogs
	commandImportDeepLink
	commandRunTask
	commandQueryAllProxy
)

const (
	resultNoError uint8 = iota
	resultCommonError
)

func apiPath(basePath string) string {
	if basePath == "" {
		basePath = internalAssetsPath
	}
	return filepath.Join(basePath, Socket)
}
