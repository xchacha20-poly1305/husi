package coresvc

import (
	"context"
	"sync"
)

// InstanceContextHolder is registered on the host base context and overwritten
// with the per-instance context each time combinedapi is constructed. Handlers
// that need instance-scoped services (URL-test history, root CA pool, NTP time)
// look it up after confirming StartedService.Instance() is non-nil.
type InstanceContextHolder struct {
	access sync.RWMutex
	ctx    context.Context
}

// NewInstanceContextHolder returns an empty holder ready to register into a
// service context.
func NewInstanceContextHolder() *InstanceContextHolder {
	return &InstanceContextHolder{}
}

// Set publishes the current instance context. Safe for concurrent use.
func (h *InstanceContextHolder) Set(ctx context.Context) {
	h.access.Lock()
	h.ctx = ctx
	h.access.Unlock()
}

// Get returns the last published instance context, or nil.
func (h *InstanceContextHolder) Get() context.Context {
	h.access.RLock()
	defer h.access.RUnlock()
	return h.ctx
}
