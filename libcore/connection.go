package libcore

import (
	"bytes"
	"cmp"
	"net"
	"net/netip"
	"reflect"
	"slices"
	"time"

	"github.com/sagernet/sing-box/experimental/clashapi"
	"github.com/sagernet/sing-box/experimental/clashapi/trafficontrol"
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
		// TODO unsafe ?
		tracker := reflect.Indirect(reflect.ValueOf(conn))
		field := reflect.Indirect(tracker.Field(1))
		metadata := field.Field(1).Interface().(trafficontrol.Metadata)
		trackerInfos = append(trackerInfos, &TrackerInfo{
			UUID:          field.Field(0).Interface().(uuid.UUID),
			Network:       metadata.NetWork,
			Src:           addressFromMetadata(metadata.SrcIP, metadata.SrcPort, "::1"),
			Dst:           addressFromMetadata(metadata.DstIP, metadata.DstPort, metadata.Host),
			UploadTotal:   field.Field(2).Interface().(*atomic.Int64),
			DownloadTotal: field.Field(3).Interface().(*atomic.Int64),
			Start:         field.Field(4).Interface().(time.Time),
			Rule:          field.Field(6).String(),
		})
	}

	slices.SortFunc(trackerInfos, func(a, b *TrackerInfo) int {
		return bytes.Compare(a.UUID.Bytes(), b.UUID.Bytes())
	})

	return &iterator[*TrackerInfo]{trackerInfos}, nil
}

func addressFromMetadata(ip netip.Addr, port, host string) string {
	if ip.IsValid() {
		return net.JoinHostPort(ip.String(), port)
	}
	return net.JoinHostPort(host, port)
}

func (b *BoxInstance) CloseConnection(id string) {
	clash, isClashServer := b.Router().ClashServer().(*clashapi.Server)
	if !isClashServer {
		return
	}

	// common.Map
	trackerList := clash.TrafficManager().Snapshot().Connections
	interfaceList := make([]interface{}, 0, len(trackerList))
	for _, tracker := range trackerList {
		interfaceList = append(interfaceList, tracker)
	}

	index, found := slices.BinarySearchFunc[[]interface{}, interface{}, interface{}](
		interfaceList,
		fakeTracker{id},
		func(a, b interface{}) int {
			return cmp.Compare(a.(idGetter).ID(), b.(idGetter).ID())
		},
	)
	if found {
		_ = trackerList[index].Close()
	}

}

type idGetter interface {
	ID() string
}

var _ idGetter = fakeTracker{}

type fakeTracker struct {
	id string
}

func (f fakeTracker) ID() string {
	return f.id
}

var _ TrackerInfoIterator = (*iterator[*TrackerInfo])(nil)

type TrackerInfoIterator interface {
	Next() *TrackerInfo
	HasNext() bool
}

type TrackerInfo struct {
	UUID          uuid.UUID
	Network       string
	Src, Dst      string
	Host          string
	UploadTotal   *atomic.Int64
	DownloadTotal *atomic.Int64
	Start         time.Time
	Rule          string
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
	return t.Start.Format(time.DateTime)
}
