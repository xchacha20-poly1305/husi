package libcore

import (
	"testing"
	"time"
)

func TestGoDebug(t *testing.T) {
	done := make(chan struct{}, 1)
	DebugFunc = func(a interface{}) {
		a.(chan struct{}) <- struct{}{}
	}
	GoDebug(done)
	select {
	case <-done:
		return
	case <-time.After(time.Second):
		t.Error("GoDebug timeout")
	}
}

func TestCatchPanic(t *testing.T) {
	defer catchPanic("TestCatchPanic", func(panicErr error) {
		t.Logf("catched panic: %v", panicErr)
	})
	panic("Test panic")
}
