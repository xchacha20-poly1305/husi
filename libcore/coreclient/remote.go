package coreclient

import (
	"github.com/sagernet/sing-box/daemon"
	E "github.com/sagernet/sing/common/exceptions"
)

func DialRemote(serverURL, secret string) (*Client, error) {
	if serverURL == "" {
		return nil, E.New("missing server URL")
	}
	conn, err := daemon.NewRemoteClient(daemon.RemoteClientOptions{
		ServerURL: serverURL,
		Secret:    secret,
	})
	if err != nil {
		return nil, E.Cause(err, "dial remote grpc")
	}
	return &Client{conn: conn, path: serverURL}, nil
}
