package libcore

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func Test_Iterator(t *testing.T) {
	tests := []uint8{9, 5, 2, 7}
	factory := newIterator(tests)
	index := 0
	for factory.HasNext() {
		assert.Equal(t, tests[index], factory.Next())
		index++
	}
	assert.Len(t, tests, index)
}
