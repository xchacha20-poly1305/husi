package demoapi

import (
	"context"
	"time"

	"github.com/sagernet/sing-box/daemon"

	"google.golang.org/grpc"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/emptypb"
)

// read runs with the state lock held; sending always happens after releasing it.
func watch[T proto.Message](s *service, ctx context.Context, send func(T) error, read func() T) error {
	var previous T
	first := true
	for {
		s.access.Lock()
		current := proto.CloneOf(read())
		changed := s.changed
		s.access.Unlock()
		if first || !proto.Equal(previous, current) {
			if err := send(current); err != nil {
				return err
			}
			first = false
			previous = current
		}
		if err := s.waitForChange(ctx, changed); err != nil {
			return err
		}
	}
}

func (s *service) waitForChange(ctx context.Context, changed <-chan struct{}) error {
	select {
	case <-ctx.Done():
		return ctx.Err()
	case <-s.ctx.Done():
		return s.ctx.Err()
	case <-changed:
		return nil
	}
}

func subscriptionInterval(nanoseconds int64) time.Duration {
	if nanoseconds <= 0 {
		return simulationInterval
	}
	return time.Duration(nanoseconds)
}

func (s *service) SubscribeStatus(request *daemon.SubscribeStatusRequest, stream grpc.ServerStreamingServer[daemon.Status]) error {
	ticker := time.NewTicker(subscriptionInterval(request.Interval))
	defer ticker.Stop()
	var previous *daemon.Status
	for {
		s.access.Lock()
		current := &daemon.Status{
			Memory:           (64 + s.tick%16) * 1024 * 1024,
			Goroutines:       int32(42 + s.activeConnections()*2),
			ConnectionsIn:    int32(s.activeConnections()),
			TrafficAvailable: true,
			UplinkTotal:      s.uploadTotal, DownlinkTotal: s.downloadTotal,
		}
		s.access.Unlock()
		if previous != nil {
			current.Uplink = current.UplinkTotal - previous.UplinkTotal
			current.Downlink = current.DownlinkTotal - previous.DownlinkTotal
		}
		if err := stream.Send(current); err != nil {
			return err
		}
		previous = current
		select {
		case <-stream.Context().Done():
			return stream.Context().Err()
		case <-s.ctx.Done():
			return s.ctx.Err()
		case <-ticker.C:
		}
	}
}

func (s *service) SubscribeConnections(request *daemon.SubscribeConnectionsRequest, stream grpc.ServerStreamingServer[daemon.ConnectionEvents]) error {
	ticker := time.NewTicker(subscriptionInterval(request.Interval))
	defer ticker.Stop()
	var previous map[string]*daemon.Connection
	includeTraffic := false
	for {
		s.access.Lock()
		current := make([]*daemon.Connection, 0, len(s.connections))
		for _, connection := range s.connections {
			current = append(current, proto.CloneOf(connection))
		}
		changed := s.changed
		s.access.Unlock()
		update := connectionUpdates(&previous, current, includeTraffic)
		if update.Reset_ || len(update.Events) != 0 {
			if err := stream.Send(update); err != nil {
				return err
			}
		}
		includeTraffic = false
		select {
		case <-stream.Context().Done():
			return stream.Context().Err()
		case <-s.ctx.Done():
			return s.ctx.Err()
		case <-changed:
		case <-ticker.C:
			includeTraffic = true
		}
	}
}

// Each subscriber tracks its own delivered totals. Notifications may coalesce;
// the retained state still provides the full delta, including a final close.
func connectionUpdates(previous *map[string]*daemon.Connection, current []*daemon.Connection, includeTraffic bool) *daemon.ConnectionEvents {
	update := &daemon.ConnectionEvents{Reset_: *previous == nil}
	retained := make(map[string]bool, len(current))
	for _, connection := range current {
		retained[connection.Id] = true
	}
	for id := range *previous {
		if !retained[id] {
			update.Reset_ = true
			break
		}
	}
	if update.Reset_ {
		*previous = make(map[string]*daemon.Connection, len(current))
	}
	for _, connection := range current {
		old := (*previous)[connection.Id]
		if old == nil {
			update.Events = append(update.Events, &daemon.ConnectionEvent{
				Type: daemon.ConnectionEventType_CONNECTION_EVENT_NEW,
				Id:   connection.Id, Connection: connection,
			})
			(*previous)[connection.Id] = connection
			continue
		}
		if old.ClosedAt != 0 {
			continue
		}
		if includeTraffic || connection.ClosedAt != 0 {
			update.Events = append(update.Events, &daemon.ConnectionEvent{
				Type:          daemon.ConnectionEventType_CONNECTION_EVENT_UPDATE,
				Id:            connection.Id,
				UplinkDelta:   connection.UplinkTotal - old.UplinkTotal,
				DownlinkDelta: connection.DownlinkTotal - old.DownlinkTotal,
			})
			(*previous)[connection.Id] = connection
		}
		if connection.ClosedAt != 0 {
			update.Events = append(update.Events, &daemon.ConnectionEvent{
				Type: daemon.ConnectionEventType_CONNECTION_EVENT_CLOSED,
				Id:   connection.Id, ClosedAt: connection.ClosedAt,
			})
		}
	}
	return update
}

func (s *service) SubscribeLog(_ *emptypb.Empty, stream grpc.ServerStreamingServer[daemon.Log]) error {
	var sequence, generation uint64
	first := true
	for {
		s.access.Lock()
		start := s.logSequence - uint64(len(s.logs))
		reset := first || generation != s.logGeneration || sequence < start
		messages := s.logs
		if !reset {
			messages = messages[sequence-start:]
		}
		update := proto.CloneOf(&daemon.Log{Reset_: reset, Messages: messages})
		sequence, generation = s.logSequence, s.logGeneration
		changed := s.changed
		s.access.Unlock()
		if reset || len(update.Messages) != 0 {
			err := stream.Send(update)
			if err != nil {
				return err
			}
		}
		first = false
		err := s.waitForChange(stream.Context(), changed)
		if err != nil {
			return err
		}
	}
}
