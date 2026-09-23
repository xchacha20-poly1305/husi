package finalmask

import (
	crand "crypto/rand"
	"math/big"
	"net"
	"time"
)

func randBetween(min, max int64) int64 {
	if min >= max {
		return min
	}
	n, err := crand.Int(crand.Reader, big.NewInt(max-min+1))
	if err != nil {
		return min
	}
	return min + n.Int64()
}

type fragmentConn struct {
	net.Conn
	config *FragmentConfig
	count  uint64
}

func NewFragmentConn(c *FragmentConfig, raw net.Conn) net.Conn {
	return &fragmentConn{
		Conn:   raw,
		config: c,
	}
}

func (c *fragmentConn) Upstream() any {
	return c.Conn
}

func (c *fragmentConn) lengthForSegment(segIdx int) (int64, int64) {
	if len(c.config.LengthsMin) == 0 {
		return 100, 200
	}
	if segIdx >= len(c.config.LengthsMin) {
		segIdx = len(c.config.LengthsMin) - 1
	}
	return c.config.LengthsMin[segIdx], c.config.LengthsMax[segIdx]
}

func (c *fragmentConn) delayForSegment(segIdx int) (int64, int64) {
	if len(c.config.DelaysMin) == 0 {
		return 0, 0
	}
	if segIdx >= len(c.config.DelaysMin) {
		segIdx = len(c.config.DelaysMin) - 1
	}
	return c.config.DelaysMin[segIdx], c.config.DelaysMax[segIdx]
}

func (c *fragmentConn) mergeTlsHelloSegments() bool {
	return len(c.config.DelaysMax) == 1 && c.config.DelaysMax[0] == 0
}

func (c *fragmentConn) Write(p []byte) (n int, err error) {
	c.count++

	// Mode 1: packets == "tlshello" (PacketsFrom == 0 && PacketsTo == 1)
	if c.config.PacketsFrom == 0 && c.config.PacketsTo == 1 {
		if c.count != 1 || len(p) <= 5 || p[0] != 22 {
			return c.Conn.Write(p)
		}
		recordLen := 5 + ((int(p[3]) << 8) | int(p[4]))
		if len(p) < recordLen {
			return c.Conn.Write(p)
		}
		data := p[5:recordLen]
		buff := make([]byte, 2048)
		var hello []byte
		mergeHello := c.mergeTlsHelloSegments()
		maxSplit := randBetween(c.config.MaxSplitMin, c.config.MaxSplitMax)
		var splitNum int64

		for from := 0; ; {
			lengthMin, lengthMax := c.lengthForSegment(int(splitNum))
			to := from + int(randBetween(lengthMin, lengthMax))
			if (to == from && splitNum > 0 && lengthMax == 0) || to > len(data) || (maxSplit > 0 && splitNum+1 >= maxSplit) {
				to = len(data)
			}
			l := to - from
			if 5+l > len(buff) {
				buff = make([]byte, 5+l)
			}
			copy(buff[:3], p)
			copy(buff[5:], data[from:to])
			from = to
			buff[3] = byte(l >> 8)
			buff[4] = byte(l)

			if mergeHello {
				hello = append(hello, buff[:5+l]...)
			} else {
				delayMin, delayMax := c.delayForSegment(int(splitNum))
				_, err := c.Conn.Write(buff[:5+l])
				if delayMax > 0 {
					d := randBetween(delayMin, delayMax)
					if d > 0 {
						time.Sleep(time.Duration(d) * time.Millisecond)
					}
				}
				if err != nil {
					return 0, err
				}
			}
			splitNum++
			if from == len(data) {
				if len(hello) > 0 {
					_, err := c.Conn.Write(hello)
					if err != nil {
						return 0, err
					}
				}
				if len(p) > recordLen {
					extraN, err := c.Conn.Write(p[recordLen:])
					if err != nil {
						return recordLen + extraN, err
					}
				}
				return len(p), nil
			}
		}
	}

	// Mode 2: PacketsFrom > 0 (e.g. packets == "1-1")
	if c.config.PacketsFrom != 0 {
		if c.count < uint64(c.config.PacketsFrom) || (c.config.PacketsTo > 0 && c.count > uint64(c.config.PacketsTo)) {
			return c.Conn.Write(p)
		}
		maxSplit := randBetween(c.config.MaxSplitMin, c.config.MaxSplitMax)
		var splitNum int64
		for from := 0; ; {
			lengthMin, lengthMax := c.lengthForSegment(int(splitNum))
			step := int(randBetween(lengthMin, lengthMax))
			if step <= 0 {
				step = 1
			}
			to := from + step
			if to > len(p) || (maxSplit > 0 && splitNum+1 >= maxSplit) {
				to = len(p)
			}
			written, err := c.Conn.Write(p[from:to])
			from += written
			if err != nil {
				return from, err
			}
			delayMin, delayMax := c.delayForSegment(int(splitNum))
			if delayMax > 0 {
				d := randBetween(delayMin, delayMax)
				if d > 0 {
					time.Sleep(time.Duration(d) * time.Millisecond)
				}
			}
			splitNum++
			if from >= len(p) {
				return from, nil
			}
		}
	}

	return c.Conn.Write(p)
}

func WrapTCPConn(raw net.Conn, cfg *FinalmaskConfig) (net.Conn, error) {
	if cfg == nil || len(cfg.TCP) == 0 {
		return raw, nil
	}

	conn := raw
	for i := len(cfg.TCP) - 1; i >= 0; i-- {
		item := cfg.TCP[i]
		if item.Type == "fragment" {
			fragCfg, err := item.Settings.BuildConfig()
			if err != nil {
				return nil, err
			}
			conn = NewFragmentConn(fragCfg, conn)
		}
	}
	return conn, nil
}
