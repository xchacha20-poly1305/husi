package libcore

import (
	"testing"
)

func TestIterator(t *testing.T) {
	tests := []uint8{9, 5, 2, 7}
	factory := iterator[uint8]{
		values: tests,
	}
	index := 0
	for factory.HasNext() {
		if factory.Next() != tests[index] {
			t.Error("failed to test iterator")
			return
		}
		index++
	}
}
