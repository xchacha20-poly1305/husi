package demoapi

import (
	"context"
	"fmt"
	"slices"
	"time"

	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/log"
	F "github.com/sagernet/sing/common/format"
	N "github.com/sagernet/sing/common/network"
)

func (s *service) appendLog(ctx context.Context, now time.Time, level log.Level, tag string, args ...any) {
	formatter := log.Formatter{BaseTime: s.startedAt, DisableLineBreak: true}
	s.logs = append(s.logs, &daemon.Log_Message{
		Level:   daemon.LogLevel(level),
		Message: formatter.Format(ctx, level, tag, F.ToString(args...), now),
	})
	s.logSequence++
	if len(s.logs) > maxLogs {
		s.logs = slices.Delete(s.logs, 0, len(s.logs)-maxLogs)
	}
}

func (s *service) appendStartupLogs(now time.Time) {
	s.appendLog(s.ctx, now, log.LevelTrace, "inbound/tun[demo-tun]", "creating stack")
	s.appendLog(s.ctx, now, log.LevelInfo, "inbound/tun[demo-tun]", "started at demo-tun")
	s.appendLog(s.ctx, now, log.LevelInfo, "", "sing-box started (0.67s)")
}

func (s *service) appendConnectionLogs(connection *daemon.Connection, id uint32, now time.Time) {
	ctx := log.ContextWithID(s.ctx, log.ID{ID: id, CreatedAt: now})
	s.connectionLogs[connection.Id] = ctx
	inbound := fmt.Sprintf("inbound/%s[%s]", connection.InboundType, connection.Inbound)
	outbound := fmt.Sprintf("outbound/%s[%s]", connection.OutboundType, connection.Outbound)
	kind := "connection"
	if connection.Network == N.NetworkUDP {
		kind = "packet connection"
	}
	// These are the TUN inbound, router sniffer and outbound dial log messages.
	s.appendLog(ctx, now, log.LevelInfo, inbound, "inbound ", kind, " from ", connection.Source)
	s.appendLog(ctx, now, log.LevelInfo, inbound, "inbound ", kind, " to ", connection.Destination)
	s.appendLog(ctx, now, log.LevelDebug, "router", "sniffed protocol: ", connection.Protocol, ", domain: ", connection.Domain)
	s.appendLog(ctx, now, log.LevelInfo, outbound, "outbound ", kind, " to ", connection.Domain, ":443")
}

func (s *service) appendConnectionClosedLogs(connection *daemon.Connection, now time.Time) {
	ctx := s.connectionLogs[connection.Id]
	// route/conn.go uses TRACE for connections closed or canceled by the caller.
	kind := "connection"
	if connection.Network == N.NetworkUDP {
		kind = "packet"
	}
	s.appendLog(ctx, now, log.LevelTrace, "connection", kind, " upload closed")
	s.appendLog(ctx, now, log.LevelTrace, "connection", kind, " download closed")
	delete(s.connectionLogs, connection.Id)
}

func (s *service) appendDNSLogs(now time.Time) {
	switch s.tick % 12 {
	case 3:
		s.appendLog(s.ctx, now, log.LevelDebug, "dns", "exchange docs.example. IN A")
		s.appendLog(s.ctx, now, log.LevelDebug, "dns", "exchanged docs.example NOERROR 300")
		s.appendLog(s.ctx, now, log.LevelInfo, "dns", "exchanged A docs.example. 300 IN A 198.51.100.20")
	case 6:
		s.appendLog(s.ctx, now, log.LevelWarn, "dns", "bad question size: 0")
	case 9:
		s.appendLog(s.ctx, now, log.LevelDebug, "dns", "exchange unavailable.example. IN A")
		s.appendLog(s.ctx, now, log.LevelError, "dns", "exchange failed for unavailable.example. IN A: context deadline exceeded")
	case 0:
		s.appendLog(s.ctx, now, log.LevelDebug, "dns", "cached docs.example NOERROR 288")
		s.appendLog(s.ctx, now, log.LevelInfo, "dns", "cached A docs.example. 288 IN A 198.51.100.20")
	}
}
