//go:build unix

package daemonhost

import (
	"os"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestPluginCredentialsSkippedWhenUnprivileged(t *testing.T) {
	if os.Geteuid() == 0 {
		t.Skip("a root daemon still has to drop plugins to the owner")
	}
	service := newDaemonDaemonService(t.TempDir(), "test", NewOwnerStore(nil))
	t.Cleanup(func() { _ = service.plugins.Close() })

	// No owner is claimed: an unprivileged daemon must not even ask for one.
	credential, err := service.pluginCredentials()
	require.NoError(t, err)
	assert.Nil(t, credential, "plugins inherit the unprivileged daemon identity")
}
