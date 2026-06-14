package libcore

import (
	"bufio"
	"cmp"
	"encoding/binary"
	"io"
	"runtime"
	"slices"
	"strings"
	"time"

	"libcore/vario"

	"github.com/sagernet/sing-box/common/trafficcontrol"
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
	trafficManager := instance.trafficManager
	var trackerInfos []*TrackerInfo
	if trafficManager == nil {
		return vario.WriteSlices(conn, trackerInfos)
	}
	activeConnections := trafficManager.Connections()
	closedConnections := trafficManager.ClosedConnections()
	trackerInfos = make([]*TrackerInfo, 0, len(activeConnections)+len(closedConnections))
	for _, metadata := range activeConnections {
		trackerInfos = append(trackerInfos, buildTrackerInfo(metadata))
	}
	for _, metadata := range closedConnections {
		trackerInfos = append(trackerInfos, buildTrackerInfo(metadata))
	}
	writer := bufio.NewWriter(conn)
	err := vario.WriteSlices(writer, trackerInfos)
	if err != nil {
		return E.Cause(err, "write connections")
	}
	return writer.Flush()
}

func buildTrackerInfo(metadata *trafficcontrol.TrackerMetadata) *TrackerInfo {
	var rule string
	if metadata.Rule == nil {
		rule = "final"
	} else {
		rule = F.ToString(metadata.Rule, " => ", metadata.Rule.Action())
	}
	var (
		processes []string
		uid       int32 = -1
	)
	if processInfo := metadata.Metadata.ProcessInfo; processInfo != nil {
		if C.IsAndroid {
			processes = slices.Clone(processInfo.AndroidPackageNames)
			uid = processInfo.UserId
		} else {
			processes = append(processes, processInfo.ProcessPath)
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
		processes:     processes,
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
	processes     []string
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

func (t *TrackerInfo) GetProcesses() StringIterator {
	return newIterator(t.processes)
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
	err = vario.WriteStringSlice(writer, t.processes)
	if err != nil {
		return E.Cause(err, "write processes")
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
	trackerInfo.processes, err = vario.ReadStringSlice(reader)
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
	ConnectionEventNew int16 = iota
	ConnectionEventUpdate
	ConnectionEventClosed
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
	trafficManager := instance.trafficManager
	if trafficManager == nil {
		return nil
	}
	subscription, done, err := trafficManager.SubscribeEvents()
	if err != nil {
		return err
	}
	defer trafficManager.UnSubscribeEvents(subscription)
	writer := bufio.NewWriter(conn)
	snapshots := make(map[uuid.UUID]connectionSnapshot, 16)
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()
	for {
		select {
		case event := <-subscription:
			var events []ConnectionEvent
			if converted := applyConnectionEvent(event, snapshots); converted != nil {
				events = append(events, *converted)
			}
		drain:
			for {
				select {
				case event = <-subscription:
					if converted := applyConnectionEvent(event, snapshots); converted != nil {
						events = append(events, *converted)
					}
				default:
					break drain
				}
			}
			err := writeConnectionEvents(writer, events)
			if err != nil {
				if E.IsClosed(err) {
					return nil
				}
				return err
			}
		case <-done:
			return nil
		case <-ticker.C:
			err := writeConnectionEvents(writer, buildTrafficUpdates(trafficManager, snapshots))
			if err != nil {
				if E.IsClosed(err) {
					return nil
				}
				return err
			}
		case <-instance.ctx.Done():
			return nil
		}
	}
}

type connectionSnapshot struct {
	uplink     int64
	downlink   int64
	hadTraffic bool
}

func applyConnectionEvent(event trafficcontrol.ConnectionEvent, snapshots map[uuid.UUID]connectionSnapshot) *ConnectionEvent {
	switch event.Type {
	case trafficcontrol.ConnectionEventNew:
		if event.Metadata == nil {
			return nil
		}
		if _, exists := snapshots[event.ID]; exists {
			return nil
		}
		snapshots[event.ID] = connectionSnapshot{
			uplink:   event.Metadata.Upload.Load(),
			downlink: event.Metadata.Download.Load(),
		}
		return &ConnectionEvent{
			Type:        ConnectionEventNew,
			ID:          event.ID.String(),
			TrackerInfo: buildTrackerInfo(event.Metadata),
		}
	case trafficcontrol.ConnectionEventClosed:
		delete(snapshots, event.ID)
		closedAt := event.ClosedAt
		if closedAt.IsZero() && event.Metadata != nil && !event.Metadata.ClosedAt.IsZero() {
			closedAt = event.Metadata.ClosedAt
		}
		if closedAt.IsZero() {
			closedAt = time.Now()
		}
		return &ConnectionEvent{
			Type:     ConnectionEventClosed,
			ID:       event.ID.String(),
			ClosedAt: closedAt.Format(time.DateTime),
		}
	default:
		return nil
	}
}

func buildTrafficUpdates(manager *trafficcontrol.Manager, snapshots map[uuid.UUID]connectionSnapshot) []ConnectionEvent {
	activeConnections := manager.Connections()
	activeIndex := make(map[uuid.UUID]*trafficcontrol.TrackerMetadata, len(activeConnections))
	var events []ConnectionEvent
	for _, metadata := range activeConnections {
		activeIndex[metadata.ID] = metadata
		currentUpload := metadata.Upload.Load()
		currentDownload := metadata.Download.Load()
		snapshot, exists := snapshots[metadata.ID]
		if !exists {
			snapshots[metadata.ID] = connectionSnapshot{
				uplink:   currentUpload,
				downlink: currentDownload,
			}
			events = append(events, ConnectionEvent{
				Type:        ConnectionEventNew,
				ID:          metadata.ID.String(),
				TrackerInfo: buildTrackerInfo(metadata),
			})
			continue
		}
		uplinkDelta := currentUpload - snapshot.uplink
		downlinkDelta := currentDownload - snapshot.downlink
		if uplinkDelta < 0 || downlinkDelta < 0 {
			if snapshot.hadTraffic {
				events = append(events, ConnectionEvent{
					Type: ConnectionEventUpdate,
					ID:   metadata.ID.String(),
				})
			}
			snapshot.uplink = currentUpload
			snapshot.downlink = currentDownload
			snapshot.hadTraffic = false
			snapshots[metadata.ID] = snapshot
			continue
		}
		if uplinkDelta > 0 || downlinkDelta > 0 {
			snapshot.uplink = currentUpload
			snapshot.downlink = currentDownload
			snapshot.hadTraffic = true
			snapshots[metadata.ID] = snapshot
			events = append(events, ConnectionEvent{
				Type:          ConnectionEventUpdate,
				ID:            metadata.ID.String(),
				UplinkDelta:   uplinkDelta,
				DownlinkDelta: downlinkDelta,
			})
			continue
		}
		if snapshot.hadTraffic {
			snapshot.hadTraffic = false
			snapshots[metadata.ID] = snapshot
			events = append(events, ConnectionEvent{
				Type: ConnectionEventUpdate,
				ID:   metadata.ID.String(),
			})
		}
	}
	var closedIndex map[uuid.UUID]*trafficcontrol.TrackerMetadata
	for id := range snapshots {
		if _, exists := activeIndex[id]; exists {
			continue
		}
		if closedIndex == nil {
			closedIndex = make(map[uuid.UUID]*trafficcontrol.TrackerMetadata)
			for _, metadata := range manager.ClosedConnections() {
				closedIndex[metadata.ID] = metadata
			}
		}
		closedAt := time.Now()
		if metadata, loaded := closedIndex[id]; loaded && !metadata.ClosedAt.IsZero() {
			closedAt = metadata.ClosedAt
		}
		events = append(events, ConnectionEvent{
			Type:     ConnectionEventClosed,
			ID:       id.String(),
			ClosedAt: closedAt.Format(time.DateTime),
		})
		delete(snapshots, id)
	}
	return events
}

func writeConnectionEvents(writer *bufio.Writer, events []ConnectionEvent) error {
	if len(events) == 0 {
		return nil
	}
	for _, event := range events {
		err := writeConnectionEvent(writer, event)
		if err != nil {
			return E.Cause(err, "write connection event")
		}
	}
	err := writer.Flush()
	if err != nil {
		return E.Cause(err, "flush connection event")
	}
	return nil
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
	if instance.trafficManager == nil {
		return nil
	}
	tracker := instance.trafficManager.Connection(uuidInstance)
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
	api.AddModeUpdateHook(subscriber)
	defer api.DeleteModeUpdateHook(subscriber)
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
