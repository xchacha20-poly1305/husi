package libcore

import (
	"net"
	"time"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
)

type Client struct {
	conn *net.UnixConn
}

func NewClient() (*Client, error) {
	var (
		conn *net.UnixConn
		err  error
	)
	for i := range 10 {
		conn, err = net.DialUnix("unix", nil, &net.UnixAddr{Name: apiPath(), Net: "unix"})
		if err == nil {
			break
		}
		time.Sleep(time.Duration(100+i*50) * time.Millisecond)
	}
	if err != nil {
		return nil, E.Cause(err, "dial unix")
	}
	return &Client{conn: conn}, nil
}

func (c *Client) Close() error {
	return common.Close(c.conn)
}
