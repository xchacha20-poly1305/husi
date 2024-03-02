package libcore

import (
	"runtime/debug"

	E "github.com/sagernet/sing/common/exceptions"
)

var DebugFunc func(interface{})

func GoDebug(a interface{}) {
	if DebugFunc != nil {
		go DebugFunc(a)
	}
}

func catchPanic(name string, handlePanic func(panicErr error)) {
	if r := recover(); r != nil {
		s := E.New(name, "panic: ", r, "\n", string(debug.Stack()))
		handlePanic(s)
	}
}
