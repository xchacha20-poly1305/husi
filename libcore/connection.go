package libcore

import (
	"time"

	"github.com/sagernet/sing-box/experimental/clashapi"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"

	"github.com/gofrs/uuid/v5"
)

func (b *BoxInstance) GetTrackerInfos() (trackerInfoIterator TrackerInfoIterator, err error) {
	clash := b.Router().ClashServer().(*clashapi.Server)
	if clash == nil {
		return nil, E.New("failed to get clash server")
	}

	connections := clash.TrafficManager().Snapshot().Connections
	trackerInfos := make([]*TrackerInfo, 0, len(connections))
	for _, conn := range connections {
		metadata := conn.Metadata()
		// TODO more information like official
		trackerInfos = append(trackerInfos, &TrackerInfo{
			UUID:          metadata.ID,
			Network:       metadata.Metadata.Network,
			Src:           metadata.Metadata.Source,
			Dst:           metadata.Metadata.Destination,
			Host:          metadata.Metadata.Domain,
			UploadTotal:   metadata.Upload.Load(),
			DownloadTotal: metadata.Download.Load(),
			Start:         metadata.CreatedAt,
			Outbound:      metadata.Outbound,
		})
	}

	return &iterator[*TrackerInfo]{trackerInfos}, nil
}

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

type TrackerInfo struct {
	UUID          uuid.UUID
	Network       string
	Src, Dst      M.Socksaddr
	Host          string
	UploadTotal   int64
	DownloadTotal int64
	Start         time.Time
	Outbound      string
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
