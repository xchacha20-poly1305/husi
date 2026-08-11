package distro

import (
	"testing"

	C "github.com/sagernet/sing-box/constant"

	"github.com/stretchr/testify/assert"
)

// A protocol whose constructor is gated behind a build tag must still register
// its option type, so that a build without that tag keeps parsing, formatting
// and generating a schema for a config using it. Only starting it may fail.
func TestBuildTaggedProtocolsKeepOptionTypes(t *testing.T) {
	outboundRegistry := OutboundRegistry()
	for _, outboundType := range []string{C.TypeNaive} {
		_, loaded := outboundRegistry.CreateOptions(outboundType)
		assert.True(t, loaded, "outbound option type %q is not registered", outboundType)
	}

	endpointRegistry := EndpointRegistry()
	for _, endpointType := range []string{C.TypeOpenConnect, C.TypeOpenVPNClient, C.TypeOpenVPNServer} {
		_, loaded := endpointRegistry.CreateOptions(endpointType)
		assert.True(t, loaded, "endpoint option type %q is not registered", endpointType)
	}
}
