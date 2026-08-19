package daemonhost

import (
	"context"
	"strconv"
	"strings"
	"sync"

	E "github.com/sagernet/sing/common/exceptions"

	husiv1 "github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/peer"
	"google.golang.org/grpc/status"
)

// PeerIdentity is the authenticated local-process identity of a gRPC client.
type PeerIdentity struct {
	// UID is the peer user id on unix platforms. Zero on Windows.
	UID uint32
	// GID is the peer primary group id on unix platforms. Zero on Windows.
	GID uint32
	// PID is the peer process id on all platforms.
	PID int32
	// SID is the peer user SID on Windows. Empty on unix.
	SID string
	// SessionID is the Windows interactive session id. Zero on unix.
	SessionID uint32
	// Username is a resolved human-readable name when available.
	Username string
}

// ID returns the stable owner key: numeric uid on unix, SID on Windows.
func (p PeerIdentity) ID() string {
	if p.SID != "" {
		return p.SID
	}
	return strconv.FormatUint(uint64(p.UID), 10)
}

// Equal reports whether p and other refer to the same local user.
func (p PeerIdentity) Equal(other PeerIdentity) bool {
	if p.SID != "" || other.SID != "" {
		return p.SID != "" && other.SID != "" && strings.EqualFold(p.SID, other.SID)
	}
	return p.UID == other.UID
}

type peerAuthInfo struct {
	credentials.CommonAuthInfo
	identity PeerIdentity
}

func (i *peerAuthInfo) AuthType() string {
	return "local-process"
}

var _ credentials.AuthInfo = (*peerAuthInfo)(nil)

type peerContextKey struct{}

// ContextWithPeerIdentity injects identity into ctx (tests and TCP dev fallback).
func ContextWithPeerIdentity(ctx context.Context, identity PeerIdentity) context.Context {
	return context.WithValue(ctx, peerContextKey{}, identity)
}

// PeerIdentityFromContext extracts the peer identity from transport credentials
// (preferred) or from a value previously set via ContextWithPeerIdentity.
func PeerIdentityFromContext(ctx context.Context) (PeerIdentity, error) {
	if identity, ok := ctx.Value(peerContextKey{}).(PeerIdentity); ok {
		return identity, nil
	}
	peerInfo, loaded := peer.FromContext(ctx)
	if !loaded || peerInfo.AuthInfo == nil {
		return platformFallbackPeerIdentity(ctx)
	}
	authInfo, ok := peerInfo.AuthInfo.(*peerAuthInfo)
	if !ok {
		return PeerIdentity{}, E.New("unexpected peer authentication type")
	}
	return authInfo.identity, nil
}

// peerConnection is a net.Conn that carries peer identity for takeover disconnect.
type peerConnection interface {
	Close() error
	peerConnectionIdentity() PeerIdentity
}

// PeerRegistry tracks authenticated peer connections so TakeOver can drop the
// previous owner's streams without stopping the running service.
type PeerRegistry struct {
	access      sync.Mutex
	connections map[peerConnection]PeerIdentity
}

// NewPeerRegistry creates an empty connection registry.
func NewPeerRegistry() *PeerRegistry {
	return &PeerRegistry{
		connections: make(map[peerConnection]PeerIdentity),
	}
}

func (r *PeerRegistry) register(connection peerConnection) {
	if r == nil {
		return
	}
	r.access.Lock()
	defer r.access.Unlock()
	if r.connections == nil {
		r.connections = make(map[peerConnection]PeerIdentity)
	}
	r.connections[connection] = connection.peerConnectionIdentity()
}

func (r *PeerRegistry) unregister(connection peerConnection) {
	if r == nil {
		return
	}
	r.access.Lock()
	defer r.access.Unlock()
	delete(r.connections, connection)
}

// DisconnectExcept closes every tracked connection whose peer is not identity.
func (r *PeerRegistry) DisconnectExcept(identity PeerIdentity) {
	if r == nil {
		return
	}
	r.access.Lock()
	var toClose []peerConnection
	for connection, peerIdentity := range r.connections {
		if !peerIdentity.Equal(identity) {
			toClose = append(toClose, connection)
		}
	}
	r.access.Unlock()
	for _, connection := range toClose {
		_ = connection.Close()
	}
}

// Authorizer decides whether a method may run for the peer in ctx.
type Authorizer interface {
	Authorize(ctx context.Context, method string) error
}

// OwnerAuthorizer enforces the plan's owner model against an OwnerStore.
// When allowAll is true (TCP --listen dev mode), every method is permitted.
type OwnerAuthorizer struct {
	owner    *OwnerStore
	allowAll bool
}

// NewOwnerAuthorizer builds an authorizer. allowAll disables checks (dev TCP).
func NewOwnerAuthorizer(owner *OwnerStore, allowAll bool) *OwnerAuthorizer {
	return &OwnerAuthorizer{owner: owner, allowAll: allowAll}
}

func (a *OwnerAuthorizer) Authorize(ctx context.Context, method string) error {
	if a == nil || a.allowAll {
		return nil
	}
	if publicMethod(method) {
		return nil
	}
	identity, err := PeerIdentityFromContext(ctx)
	if err != nil {
		return status.Error(codes.Unauthenticated, err.Error())
	}
	if a.owner == nil || !a.owner.IsOwner(identity) {
		return status.Error(codes.PermissionDenied, "the service is owned by another user")
	}
	return nil
}

func publicMethod(method string) bool {
	switch method {
	case husiv1.DaemonService_GetDaemonInfo_FullMethodName,
		husiv1.DaemonService_ClaimService_FullMethodName,
		husiv1.DaemonService_TakeOverService_FullMethodName:
		return true
	}
	// Health probes must work before claim so UI can discover a live daemon.
	if strings.HasPrefix(method, "/grpc.health.v1.Health/") {
		return true
	}
	// gRPC reflection is development-only and gated by the server registration.
	if strings.HasPrefix(method, "/grpc.reflection.") {
		return true
	}
	return false
}

// UnaryAuthorizeInterceptor rejects non-owner calls for protected methods.
func UnaryAuthorizeInterceptor(authorizer Authorizer) grpc.UnaryServerInterceptor {
	return func(ctx context.Context, req any, info *grpc.UnaryServerInfo, handler grpc.UnaryHandler) (any, error) {
		if authorizer != nil {
			if err := authorizer.Authorize(ctx, info.FullMethod); err != nil {
				return nil, err
			}
		}
		return handler(ctx, req)
	}
}

// StreamAuthorizeInterceptor is the streaming counterpart of UnaryAuthorizeInterceptor.
func StreamAuthorizeInterceptor(authorizer Authorizer) grpc.StreamServerInterceptor {
	return func(srv any, stream grpc.ServerStream, info *grpc.StreamServerInfo, handler grpc.StreamHandler) error {
		if authorizer != nil {
			if err := authorizer.Authorize(stream.Context(), info.FullMethod); err != nil {
				return err
			}
		}
		return handler(srv, stream)
	}
}
