package libcore

import (
	"testing"
)

func TestCatchPanic(t *testing.T) {
	defer catchPanic("TestCatchPanic", func(panicErr error) {
		t.Logf("catched panic: %v", panicErr)
	})
	panic("Test panic")
}
