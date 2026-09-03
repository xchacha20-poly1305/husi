package coresvc

import (
	"context"
	"unsafe"

	"github.com/sagernet/sing-box"
)

type contextPartOfBox struct {
	ctx context.Context
}

func contextFromBox(instance *box.Box) context.Context {
	if instance == nil {
		return nil
	}
	return (*contextPartOfBox)(unsafe.Pointer(instance)).ctx
}

func (h *Host) liveInstanceContext() context.Context {
	instance := h.started.Instance()
	if instance == nil {
		return nil
	}
	return contextFromBox(instance.Box())
}
