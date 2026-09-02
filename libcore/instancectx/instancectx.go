// Package instancectx carries the context sing-box builds for a running
// instance out to the husi handlers that live beside the daemon.
//
// sing's service registry is cloned for every instance, so nothing box.New
// registers inside (URL-test history, root CA pool, NTP time) is reachable from
// the context husi keeps. A Holder registered on the host context is shared by
// pointer with every clone, which makes it the one channel that leads back out.
package instancectx

import (
	"context"
	"sync"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/service"
)

// Holder stores the context of the most recently built box. Register it on the
// host context before building one, then Claim the context of the instance that
// went on to run.
type Holder struct {
	access    sync.Mutex
	published context.Context
	claimed   context.Context
}

func New() *Holder {
	return &Holder{}
}

func (h *Holder) Set(ctx context.Context) {
	h.access.Lock()
	h.published = ctx
	h.access.Unlock()
}

func (h *Holder) Claim(outbounds adapter.OutboundManager) context.Context {
	h.access.Lock()
	defer h.access.Unlock()
	if owns(h.claimed, outbounds) {
		return h.claimed
	}
	if owns(h.published, outbounds) {
		h.claimed = h.published
		return h.claimed
	}
	return nil
}

func (h *Holder) Clear() {
	h.access.Lock()
	h.published = nil
	h.claimed = nil
	h.access.Unlock()
}

// owns reports whether ctx is the context an instance of outbounds was built
// on. box.New registers the outbound manager it created on that context, so the
// manager identifies it.
func owns(ctx context.Context, outbounds adapter.OutboundManager) bool {
	if ctx == nil || outbounds == nil {
		return false
	}
	return service.FromContext[adapter.OutboundManager](ctx) == outbounds
}

// PublishingOutboundRegistry wraps registry so that building an instance
// publishes its context into the Holder registered on the host context.
//
// Outbound creation is the seam: it is the one constructor every instance husi
// can serve reaches — an instance without outbounds has nothing to URL test —
// and it runs while box.New is still assembling, long before a handler asks.
// A box built without the Holder, such as a standalone URL test on its own
// context, publishes nothing.
//
// Every box publishes, including one that is thrown away right after, so the
// context of the instance that runs is taken with Claim rather than read.
func PublishingOutboundRegistry(registry adapter.OutboundRegistry) adapter.OutboundRegistry {
	return publishingOutboundRegistry{registry}
}

type publishingOutboundRegistry struct {
	adapter.OutboundRegistry
}

func (r publishingOutboundRegistry) CreateOutbound(
	ctx context.Context,
	router adapter.Router,
	logger log.ContextLogger,
	tag string,
	outboundType string,
	options any,
) (adapter.Outbound, error) {
	if holder := service.FromContext[*Holder](ctx); holder != nil {
		// box.New tags the context of every outbound it builds. That metadata
		// belongs to the outbound being constructed, not to the instance, and
		// dropping it restores the context box.New started from: DNS rules and
		// connection attribution would otherwise read a stale outbound tag out
		// of every dial the published context serves.
		holder.Set(adapter.WithContext(ctx, nil))
	}
	return r.OutboundRegistry.CreateOutbound(ctx, router, logger, tag, outboundType, options)
}
