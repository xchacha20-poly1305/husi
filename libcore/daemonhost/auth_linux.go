//go:build linux

package daemonhost

import (
	"context"
	"net"
	"os/user"
	"strconv"
	"sync"
	"syscall"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/unix"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
)

type localTransportCredentials struct {
	registry *PeerRegistry
}

type authenticatedConnection struct {
	net.Conn
	registry   *PeerRegistry
	identity   PeerIdentity
	closeOnce  sync.Once
	closeError error
}

func PlatformServerCredentials(registry *PeerRegistry, listenTCP bool) ([]grpc.ServerOption, error) {
	if listenTCP {
		return nil, nil
	}
	return []grpc.ServerOption{
		grpc.Creds(&localTransportCredentials{registry: registry}),
	}, nil
}

func platformFallbackPeerIdentity(ctx context.Context) (PeerIdentity, error) {
	return PeerIdentity{}, E.New("missing Linux peer authentication")
}

func (c *localTransportCredentials) ClientHandshake(ctx context.Context, authority string, rawConnection net.Conn) (net.Conn, credentials.AuthInfo, error) {
	return nil, nil, E.New("local process credentials do not support client handshakes")
}

func (c *localTransportCredentials) ServerHandshake(rawConnection net.Conn) (net.Conn, credentials.AuthInfo, error) {
	identity, err := linuxPeerIdentity(rawConnection)
	if err != nil {
		return nil, nil, err
	}
	connection := &authenticatedConnection{
		Conn:     rawConnection,
		registry: c.registry,
		identity: identity,
	}
	if c.registry != nil {
		c.registry.register(connection)
	}
	return connection, &peerAuthInfo{
		CommonAuthInfo: credentials.CommonAuthInfo{SecurityLevel: credentials.PrivacyAndIntegrity},
		identity:       identity,
	}, nil
}

func (c *localTransportCredentials) Info() credentials.ProtocolInfo {
	return credentials.ProtocolInfo{
		SecurityProtocol: "linux-local-process",
	}
}

func (c *localTransportCredentials) Clone() credentials.TransportCredentials {
	return &localTransportCredentials{registry: c.registry}
}

func (c *localTransportCredentials) OverrideServerName(string) error { return nil }

func linuxPeerIdentity(connection net.Conn) (PeerIdentity, error) {
	syscallConnection, ok := connection.(syscall.Conn)
	if !ok {
		return PeerIdentity{}, E.New("daemon endpoint does not expose a syscall connection")
	}
	rawConnection, err := syscallConnection.SyscallConn()
	if err != nil {
		return PeerIdentity{}, E.Cause(err, "access daemon endpoint")
	}
	var peerCredentials *unix.Ucred
	var credentialError error
	err = rawConnection.Control(func(fileDescriptor uintptr) {
		peerCredentials, credentialError = unix.GetsockoptUcred(int(fileDescriptor), unix.SOL_SOCKET, unix.SO_PEERCRED)
	})
	if err != nil {
		return PeerIdentity{}, E.Cause(err, "inspect daemon endpoint")
	}
	if credentialError != nil {
		return PeerIdentity{}, E.Cause(credentialError, "identify daemon peer")
	}
	if peerCredentials == nil || peerCredentials.Pid <= 0 {
		return PeerIdentity{}, E.New("daemon peer has invalid credentials")
	}
	identity := PeerIdentity{
		UID: peerCredentials.Uid,
		GID: peerCredentials.Gid,
		PID: peerCredentials.Pid,
	}
	if u, lookupErr := user.LookupId(strconv.FormatUint(uint64(identity.UID), 10)); lookupErr == nil {
		identity.Username = u.Username
		if gid, parseErr := strconv.ParseUint(u.Gid, 10, 32); parseErr == nil {
			identity.GID = uint32(gid)
		}
	}
	return identity, nil
}

func (c *authenticatedConnection) peerConnectionIdentity() PeerIdentity {
	return c.identity
}

func (c *authenticatedConnection) Close() error {
	c.closeOnce.Do(func() {
		if c.registry != nil {
			c.registry.unregister(c)
		}
		c.closeError = c.Conn.Close()
	})
	return c.closeError
}

var (
	_ credentials.TransportCredentials = (*localTransportCredentials)(nil)
	_ peerConnection                   = (*authenticatedConnection)(nil)
)
