package plugindns

import (
	"context"
	"net"
	"sync"
	"time"

	"github.com/sagernet/sing-box/dns/transport"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/bufio/deadline"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/logger"

	mDNS "github.com/miekg/dns"
)

type reusableDNSCallback struct {
	access   sync.Mutex
	response *mDNS.Msg
	done     chan struct{}
}

type reusableDNSConn struct {
	net.Conn
	logger            logger.ContextLogger
	needDeadlineClose bool

	access   sync.RWMutex
	queryID  uint16
	callback map[uint16]*reusableDNSCallback

	writeLock     sync.Mutex
	startReadOnce sync.Once
}

func newReusableDNSConn(conn net.Conn, logger logger.ContextLogger, deadlineConn net.Conn) *reusableDNSConn {
	return &reusableDNSConn{
		Conn:              conn,
		logger:            logger,
		needDeadlineClose: deadline.NeedAdditionalReadDeadline(deadlineConn),
		callback:          make(map[uint16]*reusableDNSCallback),
	}
}

func (c *reusableDNSConn) nextAvailableQueryID() (uint16, error) {
	start := c.queryID
	for {
		c.queryID++
		if _, exists := c.callback[c.queryID]; !exists {
			return c.queryID, nil
		}
		if c.queryID == start {
			return 0, E.New("no available query ID")
		}
	}
}

func (c *reusableDNSConn) exchange(ctx context.Context, message *mDNS.Msg) (*mDNS.Msg, error) {
	defer setConnDeadline(ctx, c, c.needDeadlineClose)()
	err := transport.WriteMessage(c.Conn, message.Id, message)
	if err != nil {
		return nil, E.Cause(err, "write request")
	}
	response, err := transport.ReadMessage(c.Conn)
	if err != nil {
		return nil, E.Cause(err, "read response")
	}
	return response, nil
}

func setConnDeadline(ctx context.Context, conn net.Conn, needClose bool) func() {
	if needClose {
		stop := context.AfterFunc(ctx, func() {
			_ = conn.Close()
		})
		return func() { stop() }
	}
	if deadline, loaded := ctx.Deadline(); loaded {
		_ = conn.SetDeadline(deadline)
		return func() { _ = conn.SetDeadline(time.Time{}) }
	}
	return func() {}
}

func (c *reusableDNSConn) exchangePipeline(ctx context.Context, connCtx context.Context, message *mDNS.Msg, onError func(error)) (*mDNS.Msg, error) {
	c.startReadOnce.Do(func() {
		go c.recvLoop(onError)
	})

	callback := &reusableDNSCallback{
		done: make(chan struct{}),
	}

	c.access.Lock()
	queryID, err := c.nextAvailableQueryID()
	if err != nil {
		c.access.Unlock()
		return nil, err
	}
	c.callback[queryID] = callback
	c.access.Unlock()

	defer func() {
		c.access.Lock()
		delete(c.callback, queryID)
		c.access.Unlock()
	}()

	originalID := message.Id
	c.writeLock.Lock()
	err = transport.WriteMessage(c.Conn, queryID, message)
	c.writeLock.Unlock()
	if err != nil {
		if onError != nil {
			onError(err)
		}
		return nil, E.Cause(err, "write request")
	}

	select {
	case <-callback.done:
		if callback.response == nil {
			return nil, E.New("response is nil")
		}
		callback.response.Id = originalID
		return callback.response, nil
	case <-connCtx.Done():
		return nil, context.Cause(connCtx)
	case <-ctx.Done():
		return nil, ctx.Err()
	}
}

func (c *reusableDNSConn) recvLoop(onError func(error)) {
	for {
		message, err := transport.ReadMessage(c.Conn)
		if err != nil {
			if onError != nil {
				onError(E.Cause(err, "read response"))
			}
			return
		}

		c.access.RLock()
		callback, loaded := c.callback[message.Id]
		c.access.RUnlock()
		if !loaded {
			if c.logger != nil {
				c.logger.Debug("discarded unmatched pipelined response: ", message.Id)
			}
			continue
		}

		callback.access.Lock()
		select {
		case <-callback.done:
		default:
			callback.response = message
			close(callback.done)
		}
		callback.access.Unlock()
	}
}

func (c *reusableDNSConn) Close() error {
	return common.Close(c.Conn)
}
