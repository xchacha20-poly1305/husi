package libcore

import (
	"cmp"
	"runtime"
	"strings"
	"time"

	"github.com/sagernet/sing-box/experimental/clashapi"
	"github.com/sagernet/sing-box/experimental/clashapi/trafficontrol"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/memory"
	M "github.com/sagernet/sing/common/metadata"

	"github.com/gofrs/uuid/v5"
)

// GetTrackerInfos returns TrackerInfo list. If not set clash API, it will returns error.
func (b *BoxInstance) GetTrackerInfos() (trackerInfoIterator TrackerInfoIterator, err error) {
	clash := b.Router().ClashServer().(*clashapi.Server)
	if clash == nil {
		return nil, E.New("failed to get clash server")
	}

	trackerInfos := common.Map(clash.TrafficManager().Connections(), func(it trafficontrol.TrackerMetadata) *TrackerInfo {
		var rule string
		if it.Rule == nil {
			rule = "final"
		} else {
			rule = F.ToString(it.Rule, " => ", it.Rule.Outbound())
		}
		return &TrackerInfo{
			UUID:          it.ID,
			Inbound:       generateBound(it.Metadata.Inbound, it.Metadata.InboundType),
			IPVersion:     int16(it.Metadata.IPVersion),
			Network:       it.Metadata.Network,
			Src:           it.Metadata.Source,
			Dst:           it.Metadata.Destination,
			Host:          cmp.Or(it.Metadata.Domain, it.Metadata.Destination.Fqdn),
			MatchedRule:   rule,
			UploadTotal:   it.Upload.Load(),
			DownloadTotal: it.Download.Load(),
			Start:         it.CreatedAt,
			Outbound:      generateBound(it.Outbound, it.OutboundType),
			Chain:         strings.Join(it.Chain, " => "),
		}
	})

	return &iterator[*TrackerInfo]{trackerInfos}, nil
}

// CloseConnection closes the connection, whose UUID is `id`.
func (b *BoxInstance) CloseConnection(id string) {
	clash := b.Router().ClashServer().(*clashapi.Server)
	if clash == nil {
		return
	}

	trackerList := clash.TrafficManager().Snapshot().Connections
	for _, tracker := range trackerList {
		if tracker.Metadata().ID.String() == id {
			_ = tracker.Close()
			break
		}
	}
}

var _ TrackerInfoIterator = (*iterator[*TrackerInfo])(nil)

type TrackerInfoIterator interface {
	Next() *TrackerInfo
	HasNext() bool
}

// TrackerInfo recodes a connection's information.
type TrackerInfo struct {
	UUID          uuid.UUID
	Inbound       string
	IPVersion     int16
	Network       string
	Src, Dst      M.Socksaddr
	Host          string
	MatchedRule   string
	UploadTotal   int64
	DownloadTotal int64
	Start         time.Time
	Outbound      string
	Chain         string
}

func (t *TrackerInfo) GetSrc() string {
	return t.Src.String()
}

func (t *TrackerInfo) GetDst() string {
	return t.Dst.String()
}

func (t *TrackerInfo) GetUUID() string {
	return t.UUID.String()
}

func (t *TrackerInfo) GetStart() string {
	return t.Start.Format(time.DateTime)
}

// generateBound formats inbound/outbound name.
func generateBound(bound, boundType string) string {
	if bound == "" {
		return boundType
	}
	return bound + "/" + boundType
}

// GetMemory returns memory status.
func GetMemory() int64 {
	return int64(memory.Inuse())
}

// GetGoroutines returns goroutines count.
func GetGoroutines() int32 {
	return int32(runtime.NumGoroutine())
}

// GetClashModeList returns the clash mode you have set.
func (b *BoxInstance) GetClashModeList() StringIterator {
	clash := b.Router().ClashServer()
	if clash == nil {
		return nil
	}
	return &iterator[string]{clash.ModeList()}
}

// EnableClashModeCallback set whether enable clash mode callbck.
func (b *BoxInstance) EnableClashModeCallback(enable bool) {
	clash := b.Router().ClashServer().(*clashapi.Server)
	if clash == nil {
		return
	}

	// clean old
	clash.SetModeUpdateHook(nil)
	if b.clashModeHook != nil {
		select {
		case <-b.clashModeHook:
			// closed
		default:
			close(b.clashModeHook)
		}
		b.clashModeHook = nil
	}

	if !enable {
		return
	}

	b.clashModeHook = make(chan struct{})
	clash.SetModeUpdateHook(b.clashModeHook)
	go func() {
		for range b.clashModeHook {
			b.platformInterface.ClashModeCallback(clash.Mode())
		}
	}()
}

// GetClashMode returns the clash mode that being selected.
func (b *BoxInstance) GetClashMode() string {
	clash := b.Router().ClashServer()
	if clash == nil {
		return ""
	}
	return clash.Mode()
}

// SetClashMode sets clash mode.
func (b *BoxInstance) SetClashMode(mode string) {
	clash := b.Router().ClashServer().(*clashapi.Server)
	if clash == nil {
		return
	}
	clash.SetMode(mode)
}
