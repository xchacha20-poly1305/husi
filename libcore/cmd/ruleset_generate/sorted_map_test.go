package main

import (
	"slices"
	"testing"
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

	if !slices.Equal(keys, []string{"alpha", "beta", "gamma"}) {
		t.Fatalf("unexpected sorted keys: %v", keys)
	}
}
