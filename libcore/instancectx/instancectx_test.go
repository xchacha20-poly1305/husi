package instancectx_test

import (
	"context"
	"testing"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/service"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/instancectx"
)

type recordingOutboundRegistry struct {
	adapter.OutboundRegistry
	createdWith context.Context
}

func (r *recordingOutboundRegistry) CreateOutbound(
	ctx context.Context,
	router adapter.Router,
	logger log.ContextLogger,
	tag string,
	outboundType string,
	options any,
) (adapter.Outbound, error) {
	r.createdWith = ctx
	return nil, nil
}

type stubOutboundManager struct {
	adapter.OutboundManager
}

// buildBox mimics what box.New does to the host context: it extends the service
// registry, registers the outbound manager it created, then builds outbounds on
// a context tagged with the outbound being built.
func buildBox(t *testing.T, hostContext context.Context, registry adapter.OutboundRegistry) adapter.OutboundManager {
	t.Helper()
	boxContext := service.ExtendContext(hostContext)
	outbounds := &stubOutboundManager{}
	service.MustRegister[adapter.OutboundManager](boxContext, outbounds)
	_, err := registry.CreateOutbound(
		adapter.WithContext(boxContext, &adapter.InboundContext{Outbound: "proxy"}),
		nil, nil, "proxy", "direct", nil,
	)
	require.NoError(t, err)
	return outbounds
}

func TestPublishingOutboundRegistry(t *testing.T) {
	holder := instancectx.New()
	assert.Nil(t, holder.Claim(&stubOutboundManager{}), "a fresh holder has nothing to claim")

	inner := &recordingOutboundRegistry{}
	hostContext := service.ContextWith[*instancectx.Holder](context.Background(), holder)
	outbounds := buildBox(t, hostContext, instancectx.PublishingOutboundRegistry(inner))

	published := holder.Claim(outbounds)
	require.NotNil(t, published, "creating an outbound publishes the instance context")
	assert.Nil(t, adapter.ContextFrom(published), "the published context carries no outbound metadata")
	assert.Same(t, holder, service.FromContext[*instancectx.Holder](published))
	assert.NotNil(t, inner.createdWith, "the wrapped registry still builds the outbound")
	assert.Nil(t, holder.Claim(&stubOutboundManager{}), "another instance owns no published context")

	holder.Clear()
	assert.Nil(t, holder.Claim(outbounds), "a cleared holder has nothing to claim")
}

// A config check builds a box that never runs, and it publishes over the
// context of the instance that does.
func TestClaimSurvivesLaterBox(t *testing.T) {
	holder := instancectx.New()
	registry := instancectx.PublishingOutboundRegistry(&recordingOutboundRegistry{})
	hostContext := service.ContextWith[*instancectx.Holder](context.Background(), holder)

	outbounds := buildBox(t, hostContext, registry)
	claimed := holder.Claim(outbounds)
	require.NotNil(t, claimed)

	throwaway := buildBox(t, hostContext, registry)
	assert.Same(t, claimed, holder.Claim(outbounds), "the running instance keeps its context")
	assert.NotSame(t, claimed, holder.Claim(throwaway), "the throwaway box published its own")
}

func TestPublishingOutboundRegistryWithoutHolder(t *testing.T) {
	inner := &recordingOutboundRegistry{}
	registry := instancectx.PublishingOutboundRegistry(inner)

	_, err := registry.CreateOutbound(context.Background(), nil, nil, "proxy", "direct", nil)
	require.NoError(t, err)
	assert.NotNil(t, inner.createdWith, "a box without a holder still builds its outbounds")
}
