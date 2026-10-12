//go:build linux || darwin || windows

package protect

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParentProcessIDOfOwnProcess(t *testing.T) {
	parent, err := parentProcessIDOf(os.Getpid())
	require.NoError(t, err)
	assert.Equal(t, os.Getppid(), parent)
}
