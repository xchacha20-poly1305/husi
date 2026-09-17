package systemproxy

import (
	"slices"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestParseNetworkServices(t *testing.T) {
	t.Parallel()

	const output = "" +
		"An asterisk (*) denotes that a network service is disabled.\n" +
		"USB 10/100/1000 LAN\n" +
		"Wi-Fi\n" +
		"Thunderbolt Bridge\n" +
		"*Bluetooth PAN\n" +
		"\n"

	assert.Equal(t, []string{
		"USB 10/100/1000 LAN",
		"Wi-Fi",
		"Thunderbolt Bridge",
	}, slices.Collect(parseNetworkServices(output)))
}

func TestParseNetworkServicesSkipsDisabledWithoutSpace(t *testing.T) {
	t.Parallel()

	const output = "" +
		"An asterisk (*) denotes that a network service is disabled.\r\n" +
		"*iPhone USB\r\n" +
		"Ethernet\r\n"

	assert.Equal(t, []string{"Ethernet"}, slices.Collect(parseNetworkServices(output)))
}

func TestNetworksetupEnableArgs(t *testing.T) {
	t.Parallel()

	got := networksetupEnableArgs("Wi-Fi", "127.0.0.1", 2080)
	require.Equal(t, [][]string{
		{"-setwebproxy", "Wi-Fi", "127.0.0.1", "2080"},
		{"-setsecurewebproxy", "Wi-Fi", "127.0.0.1", "2080"},
		{"-setsocksfirewallproxy", "Wi-Fi", "127.0.0.1", "2080"},
	}, got)
}

func TestNetworksetupDisableArgs(t *testing.T) {
	t.Parallel()

	got := networksetupDisableArgs("Wi-Fi")
	require.Equal(t, [][]string{
		{"-setwebproxystate", "Wi-Fi", "off"},
		{"-setsecurewebproxystate", "Wi-Fi", "off"},
		{"-setsocksfirewallproxystate", "Wi-Fi", "off"},
	}, got)
}
