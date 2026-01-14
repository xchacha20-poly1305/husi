package libcore

import (
	"path/filepath"
)

const Socket = "api.sock"

const (
	commandNewInstance uint8 = iota
	commandStart
	commandStop
	commandQueryConnections
	commandCloseConnection
	commandQueryMemory
	commandQueryGoroutines
	commandQueryClashModes
	commandQuerySelectedClashMode
	commandSetClashMode
	commandNewInstanceURLTest
	commandGroupURLTest
	commandSelectOutbound
	commandQueryProxySets
	commandQueryState
	commandQuerySpeed
	commandResetNetwork
	commandStartLogWatching
	commandStopLogWatching
	commandClearLog
	commandQueryLogs
	commandPause
	commandWake
	commandNeedWIFIState
	commandQueryStats
	commandInitializeProxySet
	commandUrlTest
)

const (
	resultNoError uint8 = iota
	resultCommonError
)

func apiPath() string {
	return filepath.Join(internalAssetsPath, Socket)
}
