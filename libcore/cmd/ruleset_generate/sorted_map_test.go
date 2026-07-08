package main

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestSortedStringMapEntriesSortKeys(t *testing.T) {
	data := sortedStringMap[int]{
		"beta":  2,
		"alpha": 1,
		"gamma": 3,
	}

	var keys []string
	for _, entry := range data.Entries() {
		keys = append(keys, entry.Key)
	}

	assert.Equal(t, []string{"alpha", "beta", "gamma"}, keys)
}
