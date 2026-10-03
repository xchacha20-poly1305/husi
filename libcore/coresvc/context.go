package coresvc

import (
	"context"
	"unsafe"

	"github.com/sagernet/sing-box/daemon"
)

type contextPartOfInstance struct {
	ctx context.Context
}

func contextFromDaemon(instance *daemon.Instance) context.Context {
	if instance == nil {
		return nil
	}
	return (*contextPartOfInstance)(unsafe.Pointer(instance)).ctx
}

func (h *Host) liveInstanceContext() context.Context {
	return contextFromDaemon(h.started.Instance())
}
