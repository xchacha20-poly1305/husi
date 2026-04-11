package libcore

import (
	"net"
	"time"

	"libcore/vario"

	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
)

type Client struct {
	conn *net.UnixConn
}

func NewClient(basePath string) (*Client, error) {
	path := apiPath(basePath)
	var (
		conn *net.UnixConn
		err  error
	)
	for i := range 10 {
		conn, err = net.DialUnix("unix", nil, &net.UnixAddr{Name: path, Net: "unix"})
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

func (c *Client) Hello() error {
	err := vario.WriteUint8(c.conn, commandHello)
	if err != nil {
		return E.Cause(err, "write command")
	}
	result, err := vario.ReadUint8(c.conn)
	if err != nil {
		return E.Cause(err, "read result")
	}
	if result != resultNoError {
		return E.New("hello failed")
	}
	return nil
}

func (c *Client) ImportDeepLinks(deepLinks StringIterator) error {
	err := vario.WriteUint8(c.conn, commandImportDeepLink)
	if err != nil {
		return E.Cause(err, "write command")
	}
	err = vario.WriteStringSlice(c.conn, iteratorToArray(deepLinks))
	if err != nil {
		return E.Cause(err, "write deep link")
	}
	return nil
}

func (c *Client) RunTask(taskID string) error {
	err := vario.WriteUint8(c.conn, commandRunTask)
	if err != nil {
		return E.Cause(err, "write command")
	}
	err = vario.WriteString(c.conn, taskID)
	if err != nil {
		return E.Cause(err, "write task id")
	}
	return nil
}
