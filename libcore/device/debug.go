package device

import (
	"runtime/debug"

	E "github.com/sagernet/sing/common/exceptions"
)

var DebugFunc func(interface{})

func GoDebug(any interface{}) {
	if DebugFunc != nil {
		go DebugFunc(any)
	}
}

func DeferPanicToError(name string, err func(error)) {
	if r := recover(); r != nil {
		s := E.New(name, "panic: ", r, "\n", string(debug.Stack()))
		err(s)
	}
}
