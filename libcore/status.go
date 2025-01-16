package libcore

import (
	"cmp"
	"runtime"
	"strings"
	"time"

	"libcore/combinedapi/trafficcontrol"

	"github.com/sagernet/sing/common"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/memory"
	M "github.com/sagernet/sing/common/metadata"

	"github.com/gofrs/uuid/v5"
)

// GetTrackerInfos returns TrackerInfo list. If not set clash API, it will return error.
func (b *BoxInstance) GetTrackerInfos() (trackerInfoIterator TrackerInfoIterator, err error) {
	connections := b.api.TrafficManager().Connections()
	trackerInfos := common.Map(connections, func(it trafficcontrol.Tracker) *TrackerInfo {
		metadata := it.Metadata()
		var rule string
		if metadata.Rule == nil {
			rule = "final"
		} else {
			rule = F.ToString(metadata.Rule, " => ", metadata.Rule.Action())
		}
		return &TrackerInfo{
			UUID:          metadata.ID,
			Inbound:       generateBound(metadata.Metadata.Inbound, metadata.Metadata.InboundType),
			IPVersion:     int16(metadata.Metadata.IPVersion),
			Network:       metadata.Metadata.Network,
			Src:           metadata.Metadata.Source,
			Dst:           metadata.Metadata.Destination,
			Host:          cmp.Or(metadata.Metadata.Domain, metadata.Metadata.Destination.Fqdn),
			MatchedRule:   rule,
			UploadTotal:   metadata.Upload.Load(),
			DownloadTotal: metadata.Download.Load(),
			Start:         metadata.CreatedAt,
			Outbound:      generateBound(metadata.Outbound, metadata.OutboundType),
			Chain:         strings.Join(metadata.Chain, " => "),
			Protocol:      metadata.Metadata.Protocol,
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
