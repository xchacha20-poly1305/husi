package libcore

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func Test_CatchPanic(t *testing.T) {
	caught := false
	defer func() {
		assert.True(t, caught)
	}()
	defer catchPanic("TestCatchPanic", func(panicErr error) {
		caught = true
		require.Error(t, panicErr)
	})
	panic("Test panic")
}
