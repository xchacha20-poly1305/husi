package libcore

import (
	"cmp"
	"runtime"
	"strings"
	"time"

	"github.com/sagernet/sing-box/experimental/clashapi/trafficontrol"
	"github.com/sagernet/sing/common"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/memory"
	M "github.com/sagernet/sing/common/metadata"

	"github.com/gofrs/uuid/v5"
)

// GetTrackerInfos returns TrackerInfo list. If not set clash API, it will return error.
func (b *BoxInstance) GetTrackerInfos() (trackerInfoIterator TrackerInfoIterator, err error) {
	connections := b.api.TrafficManager().Connections()
	trackerInfos := common.Map(connections, func(it trafficontrol.TrackerMetadata) *TrackerInfo {
		var rule string
		if it.Rule == nil {
			rule = "final"
		} else {
			rule = F.ToString(it.Rule, " => ", it.Rule.Action())
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
			Protocol:      it.Metadata.Protocol,
		}
	})

	return newIterator(trackerInfos), nil
}

// CloseConnection closes the connection, whose UUID is `id`.
func (b *BoxInstance) CloseConnection(id string) {
	tracker := b.api.TrafficManager().Connection(uuid.Must(uuid.FromString(id)))
	if tracker == nil {
		return
	}
	_ = tracker.Close()
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
	Protocol      string
}

func (t *TrackerInfo) GetSrc() string {
	return t.Src.String()
}

func (t *TrackerInfo) GetDst() string {
	if !t.Dst.IsValid() {
		return ""
	}
	return t.Dst.String()
}

func (t *TrackerInfo) GetUUID() string {
	return t.UUID.String()
}

func (t *TrackerInfo) GetStart() string {
	return t.Start.Format(time.DateTime)
}

// generateBound formats inbound/outbound's name.
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
	return newIterator(b.api.ModeList())
}

// GetClashMode returns the clash mode that being selected.
func (b *BoxInstance) GetClashMode() string {
	return b.api.Mode()
}

// SetClashMode sets clash mode.
func (b *BoxInstance) SetClashMode(mode string) {
	b.api.SetMode(mode)
}
