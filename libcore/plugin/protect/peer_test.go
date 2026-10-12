package protect

import (
	"testing"

	E "github.com/sagernet/sing/common/exceptions"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestIsDescendant(t *testing.T) {
	const (
		corePID   = 100
		pluginPID = 200
		childPID  = 300
		otherPID  = 400
	)
	parents := map[int]int{
		corePID:   1,
		pluginPID: corePID,
		childPID:  pluginPID,
		otherPID:  1,
	}
	parentOf := func(processID int) (int, error) {
		parent, loaded := parents[processID]
		if !loaded {
			return 0, E.New("no such process")
		}
		return parent, nil
	}
	tests := []struct {
		name       string
		processID  int
		descendant bool
	}{
		{"self", corePID, true},
		{"child", pluginPID, true},
		{"grandchild", childPID, true},
		{"unrelated", otherPID, false},
		{"init", 1, false},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			descendant, err := isDescendant(test.processID, corePID, parentOf)
			require.NoError(t, err)
			assert.Equal(t, test.descendant, descendant)
		})
	}
}
