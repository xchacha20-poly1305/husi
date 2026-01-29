//go:build with_quic

package quic

import (
	"context"
	"time"

	"github.com/sagernet/quic-go"
	"github.com/sagernet/quic-go/congestion"
	"github.com/sagernet/sing-quic"
	"github.com/sagernet/sing-quic/congestion_bbr1"
	"github.com/sagernet/sing-quic/congestion_bbr2"
	congestion_meta1 "github.com/sagernet/sing-quic/congestion_meta1"
	congestion_meta2 "github.com/sagernet/sing-quic/congestion_meta2"
	"github.com/sagernet/sing/common/ntp"

	"github.com/xchacha20-poly1305/sing-trusttunnel"
)

func init() {
	trusttunnel.WrapH3Error = qtls.WrapError
	trusttunnel.SetCongestionControl = setCongestionControl
}

func setCongestionControl(ctx context.Context, conn *quic.Conn, name string) congestion.CongestionControl {
	timeFunc := ntp.TimeFuncFromContext(ctx)
	if timeFunc == nil {
		timeFunc = time.Now
	}
	switch name {
	case "bbr_standard":
		return congestion_bbr1.NewBbrSender(
			congestion_bbr1.DefaultClock{TimeFunc: timeFunc},
			congestion.ByteCount(conn.Config().InitialPacketSize),
			congestion_bbr1.InitialCongestionWindowPackets,
			congestion_bbr1.MaxCongestionWindowPackets,
		)
	case "bbr2":
		return congestion_bbr2.NewBBR2Sender(
			congestion_bbr2.DefaultClock{TimeFunc: timeFunc},
			congestion.ByteCount(conn.Config().InitialPacketSize),
			0,
			false,
		)
	case "bbr_variant":
		return congestion_bbr2.NewBBR2Sender(
			congestion_bbr2.DefaultClock{TimeFunc: timeFunc},
			congestion.ByteCount(conn.Config().InitialPacketSize),
			32*congestion.ByteCount(conn.Config().InitialPacketSize),
			true,
		)
	case "cubic":
		return congestion_meta1.NewCubicSender(
			congestion_meta1.DefaultClock{TimeFunc: timeFunc},
			congestion.ByteCount(conn.Config().InitialPacketSize),
			false,
		)
	case "reno":
		return congestion_meta1.NewCubicSender(
			congestion_meta1.DefaultClock{TimeFunc: timeFunc},
			congestion.ByteCount(conn.Config().InitialPacketSize),
			true,
		)
	case "", "bbr":
		fallthrough
	default:
		return congestion_meta2.NewBbrSender(
			congestion_meta2.DefaultClock{TimeFunc: timeFunc},
			congestion.ByteCount(conn.Config().InitialPacketSize),
			congestion.ByteCount(congestion_meta1.InitialCongestionWindow),
		)
	}
}
