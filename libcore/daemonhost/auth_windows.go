//go:build windows

package daemonhost

import (
	"context"
	"net"
	"os/user"
	"sync"

	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/windows"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials"
)

// fileDescriptorConnection is implemented by go-winio named pipe connections.
type fileDescriptorConnection interface {
	Fd() uintptr
}

// localTransportCredentials extracts the named-pipe client process identity.
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

// PlatformServerCredentials returns transport credentials that attach peer
// identity from GetNamedPipeClientProcessId + process token SID.
func PlatformServerCredentials(registry *PeerRegistry, listenTCP bool) ([]grpc.ServerOption, error) {
	if listenTCP {
		return nil, nil
	}
	return []grpc.ServerOption{
		grpc.Creds(&localTransportCredentials{registry: registry}),
	}, nil
}

func platformFallbackPeerIdentity(ctx context.Context) (PeerIdentity, error) {
	return PeerIdentity{}, E.New("missing Windows peer authentication")
}

func (c *localTransportCredentials) ClientHandshake(ctx context.Context, authority string, rawConnection net.Conn) (net.Conn, credentials.AuthInfo, error) {
	return nil, nil, E.New("local process credentials do not support client handshakes")
}

func (c *localTransportCredentials) ServerHandshake(rawConnection net.Conn) (net.Conn, credentials.AuthInfo, error) {
	identity, err := windowsPeerIdentity(rawConnection)
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
		SecurityProtocol: "windows-local-process",
	}
}

func (c *localTransportCredentials) Clone() credentials.TransportCredentials {
	return &localTransportCredentials{registry: c.registry}
}

func (c *localTransportCredentials) OverrideServerName(string) error { return nil }

func windowsPeerIdentity(connection net.Conn) (PeerIdentity, error) {
	descriptorConnection, ok := connection.(fileDescriptorConnection)
	if !ok {
		return PeerIdentity{}, E.New("daemon endpoint is not a Windows named pipe")
	}
	var processID uint32
	err := windows.GetNamedPipeClientProcessId(windows.Handle(descriptorConnection.Fd()), &processID)
	if err != nil {
		return PeerIdentity{}, E.Cause(err, "identify named pipe client")
	}
	if processID == 0 {
		return PeerIdentity{}, E.New("named pipe client has invalid process id")
	}
	process, err := windows.OpenProcess(windows.PROCESS_QUERY_LIMITED_INFORMATION, false, processID)
	if err != nil {
		return PeerIdentity{}, E.Cause(err, "open named pipe client process")
	}
	defer windows.CloseHandle(process)

	var token windows.Token
	err = windows.OpenProcessToken(process, windows.TOKEN_QUERY, &token)
	if err != nil {
		return PeerIdentity{}, E.Cause(err, "open named pipe client token")
	}
	defer token.Close()

	tokenUser, err := token.GetTokenUser()
	if err != nil {
		return PeerIdentity{}, E.Cause(err, "query named pipe client user")
	}
	sid := tokenUser.User.Sid.String()
	if sid == "" {
		return PeerIdentity{}, E.New("named pipe client has an invalid user SID")
	}

	var sessionID uint32
	if err = windows.ProcessIdToSessionId(processID, &sessionID); err != nil {
		return PeerIdentity{}, E.Cause(err, "query named pipe client session")
	}

	identity := PeerIdentity{
		PID:       int32(processID),
		SID:       sid,
		SessionID: sessionID,
	}
	if u, lookupErr := user.LookupId(sid); lookupErr == nil {
		identity.Username = u.Username
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
