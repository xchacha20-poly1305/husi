package libcore

import (
	"fmt"
	"net"
	"reflect"
	"time"

	"github.com/sagernet/sing-box/experimental/clashapi"
	"github.com/sagernet/sing-box/experimental/clashapi/trafficontrol"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common/atomic"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/gofrs/uuid/v5"
)

func (b *BoxInstance) GetTrackerInfos() (trackerInfoIterator TrackerInfoIterator, err error) {
	defer catchPanic("GetTrackerInfos", func(panicErr error) { err = panicErr })

	clash, isClashServer := b.Router().ClashServer().(*clashapi.Server)
	if !isClashServer {
		return nil, E.New("failed to get clash server")
	}

	connections := clash.TrafficManager().Snapshot().Connections
	trackerInfos := make([]*TrackerInfo, 0, len(connections))
	for _, conn := range connections {
		field := reflect.ValueOf(conn).Field(1)
		log.Trace(fmt.Sprintf("%v", field))
		trackerInfos = append(trackerInfos, &TrackerInfo{
			UUID:          field.Field(0).Interface().(uuid.UUID),
			Metadata:      field.Field(1).Interface().(trafficontrol.Metadata),
			UploadTotal:   field.Field(2).Interface().(*atomic.Int64),
			DownloadTotal: field.Field(3).Interface().(*atomic.Int64),
			Start:         field.Field(4).Interface().(time.Time),
			Chain:         field.Field(5).Interface().([]string),
			Rule:          field.Field(6).String(),
			RulePayload:   field.Field(7).String(),
		})
	}

	return &iterator[*TrackerInfo]{trackerInfos}, nil
}

var _ TrackerInfoIterator = (*iterator[*TrackerInfo])(nil)

type TrackerInfoIterator interface {
	Next() *TrackerInfo
	HasNext() bool
}

// TrackerInfo comes from https://github.com/SagerNet/sing-box/blob/v1.8.14/experimental/clashapi/trafficontrol/tracker.go
type TrackerInfo struct {
	UUID          uuid.UUID              `json:"id"`
	Metadata      trafficontrol.Metadata `json:"metadata"`
	UploadTotal   *atomic.Int64          `json:"upload"`
	DownloadTotal *atomic.Int64          `json:"download"`
	Start         time.Time              `json:"start"`
	Chain         []string               `json:"chains"`
	Rule          string                 `json:"rule"`
	RulePayload   string                 `json:"rulePayload"`
}

func (t TrackerInfo) GetUUID() string {
	return t.UUID.String()
}

func (t TrackerInfo) GetUploadTotal() int64 {
	return t.UploadTotal.Load()
}

func (t TrackerInfo) GetDownloadTotal() int64 {
	return t.DownloadTotal.Load()
}

func (t TrackerInfo) GetStart() string {
	return t.Start.String()
}

func (t TrackerInfo) GetNetwork() string {
	return t.Metadata.NetWork
}

func (t TrackerInfo) GetSrc() string {
	return net.JoinHostPort(t.Metadata.SrcIP.String(), t.Metadata.SrcPort)
}

func (t TrackerInfo) GetDst() string {
	return net.JoinHostPort(t.Metadata.DstIP.String(), t.Metadata.DstPort)
}

func (t TrackerInfo) GetHost() string {
	return t.Metadata.Host
}
