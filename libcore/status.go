package libcore

import (
	"cmp"
	"encoding/binary"
	"io"
	"runtime"
	"strings"
	"time"

	"libcore/combinedapi/trafficcontrol"
	"libcore/vario"

	C "github.com/sagernet/sing-box/constant"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/memory"
	"github.com/sagernet/sing/common/observable"

	"github.com/gofrs/uuid/v5"
)

func (c *Client) QueryConnections() (TrackerInfoIterator, error) {
	err := vario.WriteUint8(c.conn, commandQueryConnections)
	if err != nil {
		return nil, E.Cause(err, "write command")
	}
	trackerInfos, err := vario.ReadSlices(c.conn, readTrackerInfo)
	if err != nil {
		return nil, E.Cause(err, "read tracker infos")
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
	err := vario.WriteSlices(conn, trackerInfos)
	if err != nil {
		return E.Cause(err, "write connections")
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
		if C.IsAndroid {
			process = processInfo.AndroidPackageName
			uid = processInfo.UserId
		} else {
			process = processInfo.ProcessPath
			uid = int32(processInfo.ProcessID)
		}
	}
	var destination string
	if dest := metadata.Metadata.Destination; dest.IsValid() {
		destination = dest.String()
	}
	return &TrackerInfo{
		UUID:          metadata.ID,
		Inbound:       generateBound(metadata.Metadata.Inbound, metadata.Metadata.InboundType),
		IPVersion:     int16(metadata.Metadata.IPVersion),
		Network:       metadata.Metadata.Network,
		Src:           metadata.Metadata.Source.String(),
		Dst:           destination,
		Host:          cmp.Or(metadata.Metadata.Domain, metadata.Metadata.Destination.Fqdn),
		MatchedRule:   rule,
		UploadTotal:   metadata.Upload.Load(),
		DownloadTotal: metadata.Download.Load(),
		StartedAtUnix: unixSeconds(metadata.CreatedAt),
		ClosedAtUnix:  unixSeconds(metadata.ClosedAt),
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
	Src           string
	Dst           string
	Host          string
	MatchedRule   string
	UploadTotal   int64
	DownloadTotal int64
	StartedAtUnix int64
	ClosedAtUnix  int64
	Outbound      string
	Chain         string
	Protocol      string
	Process       string
	UID           int32
}

func (t *TrackerInfo) GetUUID() string {
	return t.UUID.String()
}

func (t *TrackerInfo) GetStartedAt() string {
	if t.StartedAtUnix == 0 {
		return ""
	}
	return time.Unix(t.StartedAtUnix, 0).Local().Format(time.DateTime)
}

func (t *TrackerInfo) GetClosedAt() string {
	if t.ClosedAtUnix == 0 {
		return ""
	}
	return time.Unix(t.ClosedAtUnix, 0).Local().Format(time.DateTime)
}

// generateBound formats inbound/outbound's name.
func generateBound(bound, boundType string) string {
	if bound == "" {
		return boundType
	}
	return bound + "/" + boundType
}

func (t *TrackerInfo) WriteToBinary(writer io.Writer) error {
	_, err := writer.Write(t.UUID[:])
	if err != nil {
		return E.Cause(err, "write uuid")
	}
	err = vario.WriteString(writer, t.Inbound)
	if err != nil {
		return E.Cause(err, "write inbound")
	}
	err = binary.Write(writer, binary.BigEndian, t.IPVersion)
	if err != nil {
		return E.Cause(err, "write ip version")
	}
	err = vario.WriteString(writer, t.Network)
	if err != nil {
		return E.Cause(err, "write network")
	}
	err = vario.WriteString(writer, t.Src)
	if err != nil {
		return E.Cause(err, "write src")
	}
	err = vario.WriteString(writer, t.Dst)
	if err != nil {
		return E.Cause(err, "write dst")
	}
	err = vario.WriteString(writer, t.Host)
	if err != nil {
		return E.Cause(err, "write host")
	}
	err = vario.WriteString(writer, t.MatchedRule)
	if err != nil {
		return E.Cause(err, "write matched rule")
	}
	err = binary.Write(writer, binary.BigEndian, t.UploadTotal)
	if err != nil {
		return E.Cause(err, "write upload total")
	}
	err = binary.Write(writer, binary.BigEndian, t.DownloadTotal)
	if err != nil {
		return E.Cause(err, "write download total")
	}
	err = binary.Write(writer, binary.BigEndian, t.StartedAtUnix)
	if err != nil {
		return E.Cause(err, "write started at unix")
	}
	err = binary.Write(writer, binary.BigEndian, t.ClosedAtUnix)
	if err != nil {
		return E.Cause(err, "write closed at unix")
	}
	err = vario.WriteString(writer, t.Outbound)
	if err != nil {
		return E.Cause(err, "write outbound")
	}
	err = vario.WriteString(writer, t.Chain)
	if err != nil {
		return E.Cause(err, "write chain")
	}
	err = vario.WriteString(writer, t.Protocol)
	if err != nil {
		return E.Cause(err, "write protocol")
	}
	err = vario.WriteString(writer, t.Process)
	if err != nil {
		return E.Cause(err, "write process")
	}
	err = binary.Write(writer, binary.BigEndian, t.UID)
	if err != nil {
		return E.Cause(err, "write uid")
	}
	return nil
}

func readTrackerInfo(reader io.Reader) (*TrackerInfo, error) {
	trackerInfo := &TrackerInfo{}
	_, err := reader.Read(trackerInfo.UUID[:])
	if err != nil {
		return nil, E.Cause(err, "read uuid")
	}
	trackerInfo.Inbound, err = vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read inbound")
	}
	err = binary.Read(reader, binary.BigEndian, &trackerInfo.IPVersion)
	if err != nil {
		return nil, E.Cause(err, "read ip version")
	}
	trackerInfo.Network, err = vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read network")
	}
	trackerInfo.Src, err = vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read src")
	}
	trackerInfo.Dst, err = vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read dst")
	}
	trackerInfo.Host, err = vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read host")
	}
	trackerInfo.MatchedRule, err = vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read matched rule")
	}
	err = binary.Read(reader, binary.BigEndian, &trackerInfo.UploadTotal)
	if err != nil {
		return nil, E.Cause(err, "read upload total")
	}
	err = binary.Read(reader, binary.BigEndian, &trackerInfo.DownloadTotal)
	if err != nil {
		return nil, E.Cause(err, "read download total")
	}
	err = binary.Read(reader, binary.BigEndian, &trackerInfo.StartedAtUnix)
	if err != nil {
		return nil, E.Cause(err, "read started at unix")
	}
	err = binary.Read(reader, binary.BigEndian, &trackerInfo.ClosedAtUnix)
	if err != nil {
		return nil, E.Cause(err, "read closed at unix")
	}
	trackerInfo.Outbound, err = vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read outbound")
	}
	trackerInfo.Chain, err = vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read chain")
	}
	trackerInfo.Protocol, err = vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read protocol")
	}
	trackerInfo.Process, err = vario.ReadString(reader)
	if err != nil {
		return nil, E.Cause(err, "read process")
	}
	err = binary.Read(reader, binary.BigEndian, &trackerInfo.UID)
	if err != nil {
		return nil, E.Cause(err, "read uid")
	}
	return trackerInfo, nil
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

func unixSeconds(value time.Time) int64 {
	if value.IsZero() {
		return 0
	}
	return value.Unix()
}

func toConnectionEvent(event trafficcontrol.ConnectionEvent) ConnectionEvent {
	converted := ConnectionEvent{
		Type: int16(event.Type),
		ID:   event.ID.String(),
	}
	switch event.Type {
	case trafficcontrol.ConnectionEventNew:
		converted.TrackerInfo = buildTrackerInfo(event.Metadata)
	case trafficcontrol.ConnectionEventUpdate:
		converted.UplinkDelta = event.UplinkDelta
		converted.DownlinkDelta = event.DownlinkDelta
	case trafficcontrol.ConnectionEventClosed:
		converted.ClosedAt = event.ClosedAt.Format(time.DateTime)
	}
	return converted
}

type ConnectionEventCallback interface {
	OnConnectionEvent(*ConnectionEvent)
}

func (c *Client) SubscribeConnectionEvent(callback ConnectionEventCallback) error {
	err := vario.WriteUint8(c.conn, commandSubscribeConnections)
	if err != nil {
		return E.Cause(err, "write command")
	}
	for {
		event, err := readConnectionEvent(c.conn)
		if err != nil {
			return E.Cause(err, "read event")
		}
		callback.OnConnectionEvent(&event)
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
			err := writeConnectionEvent(conn, toConnectionEvent(event))
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
	err = vario.WriteUint8(c.conn, commandCloseConnection)
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
	err := vario.WriteUint8(c.conn, commandQueryMemory)
	if err != nil {
		return 0, E.Cause(err, "write command")
	}
	memoryInuse, err := vario.ReadInt64(c.conn)
	if err != nil {
		return 0, E.Cause(err, "read memory")
	}
	return memoryInuse, nil
}

func (s *Service) handleQueryMemory(conn io.ReadWriter) error {
	err := vario.WriteInt64(conn, int64(memory.Inuse()))
	if err != nil {
		return E.Cause(err, "write memory")
	}
	return nil
}

func (c *Client) QueryGoroutines() (int32, error) {
	err := vario.WriteUint8(c.conn, commandQueryGoroutines)
	if err != nil {
		return 0, E.Cause(err, "write command")
	}
	goroutines, err := vario.ReadInt32(c.conn)
	if err != nil {
		return 0, E.Cause(err, "read goroutines")
	}
	return goroutines, nil
}

func (s *Service) handleQueryGoroutines(conn io.ReadWriter) error {
	err := vario.WriteInt32(conn, int32(runtime.NumGoroutine()))
	if err != nil {
		return E.Cause(err, "write goroutines")
	}
	return nil
}

func (c *Client) QueryClashModes() (StringIterator, error) {
	err := vario.WriteUint8(c.conn, commandQueryClashModes)
	if err != nil {
		return nil, E.Cause(err, "write command")
	}
	modes, err := vario.ReadStringSlice(c.conn)
	if err != nil {
		return nil, E.Cause(err, "read clash modes")
	}
	return newIterator(modes), nil
}

func (s *Service) handleQueryClashModes(conn io.ReadWriter, instance *boxInstance) error {
	err := vario.WriteStringSlice(conn, instance.api.ModeList())
	if err != nil {
		return E.Cause(err, "write clash modes")
	}
	return nil
}

func (c *Client) SubscribeClashMode(callback StringFunc) error {
	err := vario.WriteUint8(c.conn, commandSubscribeClashMode)
	if err != nil {
		return E.Cause(err, "write command")
	}
	for {
		mode, err := vario.ReadString(c.conn)
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
	err := vario.WriteString(conn, api.Mode())
	if err != nil {
		return E.Cause(err, "write first mode")
	}
	subscription, done := subscriber.Subscription()
	for {
		select {
		case <-subscription:
			err = vario.WriteString(conn, api.Mode())
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
	err := vario.WriteUint8(c.conn, commandSetClashMode)
	if err != nil {
		return E.Cause(err, "write command")
	}
	err = vario.WriteString(c.conn, mode)
	if err != nil {
		return E.Cause(err, "write clash mode")
	}
	return nil
}

func (s *Service) handleSetClashMode(conn io.ReadWriter, instance *boxInstance) error {
	mode, err := vario.ReadString(conn)
	if err != nil {
		return E.Cause(err, "read clash mode")
	}
	instance.api.SetMode(mode)
	return nil
}

func (c *Client) ResetNetwork() error {
	err := vario.WriteUint8(c.conn, commandResetNetwork)
	if err != nil {
		return E.Cause(err, "write command")
	}
	return nil
}

func writeConnectionEvent(writer io.Writer, event ConnectionEvent) error {
	err := vario.WriteInt16(writer, event.Type)
	if err != nil {
		return E.Cause(err, "write type")
	}
	err = vario.WriteString(writer, event.ID)
	if err != nil {
		return E.Cause(err, "write id")
	}
	switch event.Type {
	case ConnectionEventNew:
		if event.TrackerInfo == nil {
			return E.New("tracker info is nil")
		}
		if err := event.TrackerInfo.WriteToBinary(writer); err != nil {
			return E.Cause(err, "write tracker info")
		}
	case ConnectionEventUpdate:
		err = vario.WriteInt64(writer, event.UplinkDelta)
		if err != nil {
			return E.Cause(err, "write uplink delta")
		}
		err = vario.WriteInt64(writer, event.DownlinkDelta)
		if err != nil {
			return E.Cause(err, "write downlink delta")
		}
	case ConnectionEventClosed:
		err = vario.WriteString(writer, event.ClosedAt)
		if err != nil {
			return E.Cause(err, "write closed at")
		}
	default:
		return E.New("unknown event type: ", event.Type)
	}
	return nil
}

func readConnectionEvent(reader io.Reader) (ConnectionEvent, error) {
	eventType, err := vario.ReadInt16(reader)
	if err != nil {
		return ConnectionEvent{}, E.Cause(err, "read type")
	}
	id, err := vario.ReadString(reader)
	if err != nil {
		return ConnectionEvent{}, E.Cause(err, "read id")
	}
	event := ConnectionEvent{
		Type: eventType,
		ID:   id,
	}
	switch eventType {
	case ConnectionEventNew:
		trackerInfo, err := readTrackerInfo(reader)
		if err != nil {
			return ConnectionEvent{}, E.Cause(err, "read tracker info")
		}
		event.TrackerInfo = trackerInfo
	case ConnectionEventUpdate:
		event.UplinkDelta, err = vario.ReadInt64(reader)
		if err != nil {
			return ConnectionEvent{}, E.Cause(err, "read uplink delta")
		}
		event.DownlinkDelta, err = vario.ReadInt64(reader)
		if err != nil {
			return ConnectionEvent{}, E.Cause(err, "read downlink delta")
		}
	case ConnectionEventClosed:
		event.ClosedAt, err = vario.ReadString(reader)
		if err != nil {
			return ConnectionEvent{}, E.Cause(err, "read closed at")
		}
	default:
		return ConnectionEvent{}, E.New("unknown event type: ", eventType)
	}
	return event, nil
}
