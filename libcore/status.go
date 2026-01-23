package libcore

import (
	"cmp"
	"io"
	"runtime"
	"strings"
	"time"

	"libcore/combinedapi/trafficcontrol"

	"github.com/sagernet/sing/common/binary"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/memory"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/common/observable"
	"github.com/sagernet/sing/common/varbin"

	"github.com/gofrs/uuid/v5"
)

func (c *Client) QueryConnections() (TrackerInfoIterator, error) {
	err := varbin.Write(c.conn, binary.BigEndian, commandQueryConnections)
	if err != nil {
		return nil, E.Cause(err, "write command")
	}
	trackerInfos, err := varbin.ReadValue[[]*TrackerInfo](c.conn, binary.BigEndian)
	if err != nil {
		return nil, E.Cause(err, "read tracker info")
	}
	return newIterator(trackerInfos), nil
}

func (s *Service) handleQueryConnections(conn io.ReadWriter, instance *boxInstance) error {
	metadatas := instance.api.TrafficManager().ClosedConnections()
	trackerInfos := make([]*TrackerInfo, 0, len(metadatas))
	for _, metadata := range metadatas {
		trackerInfos = append(trackerInfos, buildTrackerInfo(metadata))
	}
	instance.api.TrafficManager().Range(func(_ uuid.UUID, tracker trafficcontrol.Tracker) bool {
		trackerInfos = append(trackerInfos, buildTrackerInfo(tracker.Metadata()))
		return true
	})
	err := varbin.Write(conn, binary.BigEndian, trackerInfos)
	if err != nil {
		return E.Cause(err, "write tracker infos")
	}
	return nil
}

func buildTrackerInfo(metadata trafficcontrol.TrackerMetadata) *TrackerInfo {
	var rule string
	if metadata.Rule == nil {
		rule = "final"
	} else {
		rule = F.ToString(metadata.Rule, " => ", metadata.Rule.Action())
	}
	var (
		process string
		uid     int32 = -1
	)
	if processInfo := metadata.Metadata.ProcessInfo; processInfo != nil {
		process = processInfo.AndroidPackageName
		uid = processInfo.UserId
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
		startedAt:     metadata.CreatedAt,
		closedAt:      metadata.ClosedAt,
		Outbound:      generateBound(metadata.Outbound, metadata.OutboundType),
		Chain:         strings.Join(metadata.Chain, " => "),
		Protocol:      metadata.Metadata.Protocol,
		Process:       process,
		UID:           uid,
	}
}

var _ TrackerInfoIterator = (*iterator[*TrackerInfo])(nil)

type TrackerInfoIterator interface {
	Next() *TrackerInfo
	HasNext() bool
	Length() int32
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
	startedAt     time.Time
	closedAt      time.Time
	Outbound      string
	Chain         string
	Protocol      string
	Process       string
	UID           int32
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

func (t *TrackerInfo) GetStartedAt() string {
	return t.startedAt.Format(time.DateTime)
}

func (t *TrackerInfo) GetClosedAt() string {
	if t.closedAt.IsZero() {
		return ""
	}
	return t.closedAt.Format(time.DateTime)
}

// generateBound formats inbound/outbound's name.
func generateBound(bound, boundType string) string {
	if bound == "" {
		return boundType
	}
	return bound + "/" + boundType
}

const (
	ConnectionEventNew    = int16(trafficcontrol.ConnectionEventNew)
	ConnectionEventUpdate = int16(trafficcontrol.ConnectionEventUpdate)
	ConnectionEventClosed = int16(trafficcontrol.ConnectionEventClosed)
)

type ConnectionEvent struct {
	Type          int16
	ID            string
	TrackerInfo   *TrackerInfo
	UplinkDelta   int64
	DownlinkDelta int64
	ClosedAt      string
}

type ConnectionEventCallback interface {
	OnConnectionEvent(*ConnectionEvent)
}

func (c *Client) SubscribeConnectionEvent(callback ConnectionEventCallback) error {
	err := varbin.Write(c.conn, binary.BigEndian, commandSubscribeConnections)
	if err != nil {
		return E.Cause(err, "write command")
	}
	for {
		event, err := varbin.ReadValue[trafficcontrol.ConnectionEvent](c.conn, binary.BigEndian)
		if err != nil {
			return err
		}
		callbackEvent := &ConnectionEvent{
			Type: int16(event.Type),
			ID:   event.ID.String(),
		}
		switch event.Type {
		case trafficcontrol.ConnectionEventNew:
			callbackEvent.TrackerInfo = buildTrackerInfo(event.Metadata)
		case trafficcontrol.ConnectionEventUpdate:
			callbackEvent.UplinkDelta = event.UplinkDelta
			callbackEvent.DownlinkDelta = event.DownlinkDelta
		case trafficcontrol.ConnectionEventClosed:
			callbackEvent.ClosedAt = event.ClosedAt.Format(time.DateTime)
		}
		callback.OnConnectionEvent(callbackEvent)
	}
}

func (s *Service) handleSubscribeConnections(conn io.ReadWriter, instance *boxInstance) error {
	subscriber := observable.NewSubscriber[trafficcontrol.ConnectionEvent](256)
	defer subscriber.Close()
	trafficManager := instance.api.TrafficManager()
	trafficManager.SetEventHook(subscriber)
	defer trafficManager.SetEventHook(nil)
	subscription, done := subscriber.Subscription()
	for {
		select {
		case event := <-subscription:
			err := varbin.Write(conn, binary.BigEndian, event)
			if err != nil {
				if E.IsClosed(err) {
					return nil
				}
				return E.Cause(err, "write connection event")
			}
		case <-done:
			return nil
		case <-instance.ctx.Done():
			return nil
		}
	}
}

func (c *Client) CloseConnection(uuidString string) error {
	uuidInstance, err := uuid.FromString(uuidString)
	if err != nil {
		return err
	}
	err = varbin.Write(c.conn, binary.BigEndian, commandCloseConnection)
	if err != nil {
		return E.Cause(err, "write command")
	}
	_, err = c.conn.Write(uuidInstance[:])
	if err != nil {
		return E.Cause(err, "write uuid")
	}
	return nil
}

func (s *Service) handleCloseConnection(conn io.ReadWriter, instance *boxInstance) error {
	var uuidInstance uuid.UUID
	_, err := io.ReadFull(conn, uuidInstance[:])
	if err != nil {
		return E.Cause(err, "read uuid")
	}
	tracker := instance.api.TrafficManager().Connection(uuidInstance)
	if tracker == nil {
		return nil
	}
	_ = tracker.Close()
	return nil
}

func (c *Client) QueryMemory() (int64, error) {
	err := varbin.Write(c.conn, binary.BigEndian, commandQueryMemory)
	if err != nil {
		return 0, E.Cause(err, "write command")
	}
	memoryInuse, err := varbin.ReadValue[int64](c.conn, binary.BigEndian)
	if err != nil {
		return 0, E.Cause(err, "read memory")
	}
	return memoryInuse, nil
}

func (s *Service) handleQueryMemory(conn io.ReadWriter) error {
	err := varbin.Write(conn, binary.BigEndian, int64(memory.Inuse()))
	if err != nil {
		return E.Cause(err, "write memory")
	}
	return nil
}

func (c *Client) QueryGoroutines() (int32, error) {
	err := varbin.Write(c.conn, binary.BigEndian, commandQueryGoroutines)
	if err != nil {
		return 0, E.Cause(err, "write command")
	}
	goroutines, err := varbin.ReadValue[int32](c.conn, binary.BigEndian)
	if err != nil {
		return 0, E.Cause(err, "read goroutines")
	}
	return goroutines, nil
}

func (s *Service) handleQueryGoroutines(conn io.ReadWriter) error {
	err := varbin.Write(conn, binary.BigEndian, int32(runtime.NumGoroutine()))
	if err != nil {
		return E.Cause(err, "write goroutines")
	}
	return nil
}

func (c *Client) QueryClashModes() (StringIterator, error) {
	err := varbin.Write(c.conn, binary.BigEndian, commandQueryClashModes)
	if err != nil {
		return nil, E.Cause(err, "write command")
	}
	modes, err := varbin.ReadValue[[]string](c.conn, binary.BigEndian)
	if err != nil {
		return nil, E.Cause(err, "read clash modes")
	}
	return newIterator(modes), nil
}

func (s *Service) handleQueryClashModes(conn io.ReadWriter, instance *boxInstance) error {
	err := varbin.Write(conn, binary.BigEndian, instance.api.ModeList())
	if err != nil {
		return E.Cause(err, "write clash modes")
	}
	return nil
}

func (c *Client) SubscribeClashMode(callback StringFunc) error {
	err := varbin.Write(c.conn, binary.BigEndian, commandSubscribeClashMode)
	if err != nil {
		return E.Cause(err, "write command")
	}
	for {
		mode, err := varbin.ReadValue[string](c.conn, binary.BigEndian)
		if err != nil {
			return E.Cause(err, "read clash mode")
		}
		callback.Invoke(mode)
	}
}

func (s *Service) handleSubscribeClashMode(conn io.ReadWriter, instance *boxInstance) error {
	subscriber := observable.NewSubscriber[struct{}](1)
	defer subscriber.Close()
	api := instance.api
	api.SetModeUpdateHook(subscriber)
	defer api.SetModeUpdateHook(nil)
	err := varbin.Write(conn, binary.BigEndian, api.Mode())
	if err != nil {
		return E.Cause(err, "write first mode")
	}
	subscription, done := subscriber.Subscription()
	for {
		select {
		case <-subscription:
			err = varbin.Write(conn, binary.BigEndian, api.Mode())
			if err != nil {
				return E.Cause(err, "write clash mode")
			}
		case <-instance.ctx.Done():
			return nil
		case <-done:
			return nil
		}
	}
}

func (c *Client) SetClashMode(mode string) error {
	err := varbin.Write(c.conn, binary.BigEndian, commandSetClashMode)
	if err != nil {
		return E.Cause(err, "write command")
	}
	err = varbin.Write(c.conn, binary.BigEndian, mode)
	if err != nil {
		return E.Cause(err, "write clash mode")
	}
	return nil
}

func (s *Service) handleSetClashMode(conn io.ReadWriter, instance *boxInstance) error {
	mode, err := varbin.ReadValue[string](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read clash mode")
	}
	instance.api.SetMode(mode)
	return nil
}

func (c *Client) ResetNetwork() error {
	err := varbin.Write(c.conn, binary.BigEndian, commandResetNetwork)
	if err != nil {
		return E.Cause(err, "write command")
	}
	return nil
}
