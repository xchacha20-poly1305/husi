package daemonhost

import (
	"sync"

	husiv1 "github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

type OwnerStore struct {
	access   sync.RWMutex
	owner    *PeerIdentity
	registry *PeerRegistry
}

func NewOwnerStore(registry *PeerRegistry) *OwnerStore {
	return &OwnerStore{registry: registry}
}

func (o *OwnerStore) Claim(identity PeerIdentity) error {
	o.access.Lock()
	defer o.access.Unlock()
	if o.owner == nil {
		cloned := identity
		o.owner = &cloned
		return nil
	}
	if o.owner.Equal(identity) {
		// Refresh username / session metadata from the latest connection.
		cloned := identity
		o.owner = &cloned
		return nil
	}
	return status.Error(codes.Aborted, "the service was claimed by another user")
}

func (o *OwnerStore) TakeOver(identity PeerIdentity) error {
	o.access.Lock()
	cloned := identity
	o.owner = &cloned
	registry := o.registry
	o.access.Unlock()
	if registry != nil {
		registry.DisconnectExcept(identity)
	}
	return nil
}

func (o *OwnerStore) IsOwner(identity PeerIdentity) bool {
	o.access.RLock()
	defer o.access.RUnlock()
	return o.owner != nil && o.owner.Equal(identity)
}

func (o *OwnerStore) HasOwner() bool {
	o.access.RLock()
	defer o.access.RUnlock()
	return o.owner != nil
}

func (o *OwnerStore) Owner() *PeerIdentity {
	o.access.RLock()
	defer o.access.RUnlock()
	if o.owner == nil {
		return nil
	}
	cloned := *o.owner
	return &cloned
}

func (o *OwnerStore) SetOwner(identity PeerIdentity) {
	o.access.Lock()
	defer o.access.Unlock()
	cloned := identity
	o.owner = &cloned
}

func (o *OwnerStore) Clear() {
	o.access.Lock()
	defer o.access.Unlock()
	o.owner = nil
}

func (o *OwnerStore) OwnershipInfo(caller *PeerIdentity) *husiv1.Ownership {
	o.access.RLock()
	defer o.access.RUnlock()
	if o.owner == nil {
		return &husiv1.Ownership{}
	}
	info := &husiv1.Ownership{
		Claimed:   true,
		OwnerId:   o.owner.ID(),
		OwnerName: o.owner.Username,
	}
	if caller != nil && o.owner.Equal(*caller) {
		info.OwnedByCaller = true
	}
	return info
}
