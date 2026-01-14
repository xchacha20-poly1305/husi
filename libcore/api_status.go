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
	"github.com/sagernet/sing/common/varbin"

	"github.com/gofrs/uuid/v5"
)

const (
	ShowTrackerActively uint8 = 1 << 0
	ShowTrackerClosed   uint8 = 1 << 1
)

const (
	defaultTestLink    = "https://www.gstatic.com/generate_204"
	defaultTestTimeout = 5000 // ms
)

func (c *Client) QueryConnections(filterFlag uint8) (TrackerInfoIterator, error) {
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandQueryConnections)
	if err != nil {
		return nil, E.Cause(err, "write command")
	}
	err = varbin.Write(c.conn, binary.BigEndian, filterFlag)
	if err != nil {
		return nil, E.Cause(err, "write filter flag")
	}
	trackerInfos, err := varbin.ReadValue[[]*TrackerInfo](c.conn, binary.BigEndian)
	if err != nil {
		return nil, E.Cause(err, "read tracker info")
	}
	return newIterator(trackerInfos), nil
}

func (s *Service) handleQueryConnections(conn io.ReadWriter, instance *boxInstance) error {
	filterFlag, err := varbin.ReadValue[uint8](conn, binary.BigEndian)
	if err != nil {
		return E.Cause(err, "read filter flag")
	}
	var trackerInfos []*TrackerInfo
	if filterFlag&ShowTrackerClosed != 0 {
		metadatas := instance.api.TrafficManager().ClosedConnectionsMetadata()
		trackerInfos = make([]*TrackerInfo, 0, len(metadatas))
		for _, metadata := range metadatas {
			trackerInfos = append(trackerInfos, buildTrackerInfo(metadata, true))
		}
	}
	if filterFlag&ShowTrackerActively != 0 {
		instance.api.TrafficManager().Range(func(_ uuid.UUID, tracker trafficcontrol.Tracker) bool {
			trackerInfos = append(trackerInfos, buildTrackerInfo(tracker.Metadata(), false))
			return true
		})
	}
	err = varbin.Write(conn, binary.BigEndian, trackerInfos)
	if err != nil {
		return E.Cause(err, "write tracker infos")
	}
	return nil
}

func buildTrackerInfo(metadata trafficcontrol.TrackerMetadata, closed bool) *TrackerInfo {
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
		start:         metadata.CreatedAt,
		Outbound:      generateBound(metadata.Outbound, metadata.OutboundType),
		Chain:         strings.Join(metadata.Chain, " => "),
		Protocol:      metadata.Metadata.Protocol,
		Process:       process,
		UID:           uid,
		Closed:        closed,
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
	start         time.Time
	Outbound      string
	Chain         string
	Protocol      string
	Process       string
	UID           int32
	Closed        bool
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
	return t.start.Format(time.DateTime)
}

// generateBound formats inbound/outbound's name.
func generateBound(bound, boundType string) string {
	if bound == "" {
		return boundType
	}
	return bound + "/" + boundType
}

func (c *Client) CloseConnection(uuidString string) error {
	uuidInstance, err := uuid.FromString(uuidString)
	if err != nil {
		return err
	}
	c.access.Lock()
	defer c.access.Unlock()
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
	c.access.Lock()
	defer c.access.Unlock()
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
	c.access.Lock()
	defer c.access.Unlock()
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
	c.access.Lock()
	defer c.access.Unlock()
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

func (c *Client) QuerySelectedClashMode() (string, error) {
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandQuerySelectedClashMode)
	if err != nil {
		return "", E.Cause(err, "write command")
	}
	mode, err := varbin.ReadValue[string](c.conn, binary.BigEndian)
	if err != nil {
		return "", E.Cause(err, "read selected clash mode")
	}
	return mode, nil
}

func (s *Service) handleQuerySelectedClashMode(conn io.ReadWriter, instance *boxInstance) error {
	err := varbin.Write(conn, binary.BigEndian, instance.api.Mode())
	if err != nil {
		return E.Cause(err, "write selected clash mode")
	}
	return nil
}

func (c *Client) SetClashMode(mode string) error {
	c.access.Lock()
	defer c.access.Unlock()
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
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandResetNetwork)
	if err != nil {
		return E.Cause(err, "write command")
	}
	return nil
}

func (c *Client) QueryLogs() (LogItemIterator, error) {
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandQueryLogs)
	if err != nil {
		return nil, E.Cause(err, "write command")
	}
	logs, err := varbin.ReadValue[[]*LogItem](c.conn, binary.BigEndian)
	if err != nil {
		return nil, E.Cause(err, "read logs")
	}
	return newIterator(logs), nil
}

func (s *Service) handleQueryLogs(conn io.ReadWriter) error {
	var logs []*LogItem
	if platformLogWrapper != nil {
		logs = platformLogWrapper.buffer.All()
	}
	err := varbin.Write(conn, binary.BigEndian, logs)
	if err != nil {
		return E.Cause(err, "write logs")
	}
	return nil
}

func (c *Client) ClearLog() error {
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandClearLog)
	if err != nil {
		return E.Cause(err, "write command")
	}
	return nil
}

func (c *Client) Pause() error {
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandPause)
	if err != nil {
		return E.Cause(err, "write command")
	}
	return nil
}

func (c *Client) Wake() error {
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandWake)
	if err != nil {
		return E.Cause(err, "write command")
	}
	return nil
}

func (c *Client) NeedWIFIState() (bool, error) {
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandNeedWIFIState)
	if err != nil {
		return false, E.Cause(err, "write command")
	}
	needWIFI, err := varbin.ReadValue[bool](c.conn, binary.BigEndian)
	if err != nil {
		return false, E.Cause(err, "read need wifi state")
	}
	return needWIFI, nil
}

func (c *Client) QueryStats(tag string, isUpload bool) (int64, error) {
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandQueryStats)
	if err != nil {
		return 0, E.Cause(err, "write command")
	}
	err = varbin.Write(c.conn, binary.BigEndian, tag)
	if err != nil {
		return 0, E.Cause(err, "write tag")
	}
	err = varbin.Write(c.conn, binary.BigEndian, isUpload)
	if err != nil {
		return 0, E.Cause(err, "write isUpload")
	}
	stats, err := varbin.ReadValue[int64](c.conn, binary.BigEndian)
	if err != nil {
		return 0, E.Cause(err, "read stats")
	}
	return stats, nil
}

func (c *Client) InitializeProxySet() error {
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandInitializeProxySet)
	if err != nil {
		return E.Cause(err, "write command")
	}
	return nil
}

func (c *Client) UrlTest(tag string) (int32, error) {
	c.access.Lock()
	defer c.access.Unlock()
	err := varbin.Write(c.conn, binary.BigEndian, commandUrlTest)
	if err != nil {
		return -1, E.Cause(err, "write command")
	}
	// tag can be empty to test default outbound
	err = varbin.Write(c.conn, binary.BigEndian, tag)
	if err != nil {
		return -1, E.Cause(err, "write tag")
	}
	// Use default link and timeout
	err = varbin.Write(c.conn, binary.BigEndian, defaultTestLink)
	if err != nil {
		return -1, E.Cause(err, "write link")
	}
	err = varbin.Write(c.conn, binary.BigEndian, int32(defaultTestTimeout))
	if err != nil {
		return -1, E.Cause(err, "write timeout")
	}
	resultCode, err := varbin.ReadValue[uint8](c.conn, binary.BigEndian)
	if err != nil {
		return -1, E.Cause(err, "read result code")
	}
	if resultCode != resultNoError {
		errMsg, err := varbin.ReadValue[string](c.conn, binary.BigEndian)
		if err != nil {
			return -1, E.Cause(err, "read error message")
		}
		return -1, E.New(errMsg)
	}
	latency, err := varbin.ReadValue[int32](c.conn, binary.BigEndian)
	if err != nil {
		return -1, E.Cause(err, "read latency")
	}
	return latency, nil
}
