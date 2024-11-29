// Package v2rayapilite is copied form github.com/sagernet/sing-box/experimental/v2rayapi ,
// implementing a V2Ray server,
// which not based on gRPC but has API for libcore.
package v2rayapilite

import (
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/experimental"
	_ "github.com/sagernet/sing-box/include" // Make v2rayapilite be imported after it
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"
)

func init() {
	experimental.RegisterV2RayServerConstructor(NewServer)
}

const Name = "v2ray server lite"

var (
	_ adapter.V2RayServer = (*V2rayServer)(nil)
	_ StatsGetter         = (*V2rayServer)(nil)
)

type StatsGetter interface {
	QueryStats(name string) int64
}

type V2rayServer struct {
	statsService *StatsService
}

func (v *V2rayServer) QueryStats(name string) int64 {
	return v.statsService.QueryStats(name)
}

func NewServer(_ log.Logger, options option.V2RayAPIOptions) (adapter.V2RayServer, error) {
	return &V2rayServer{
		statsService: NewStatsService(common.PtrValueOrDefault(options.Stats)),
	}, nil
}

func (v *V2rayServer) Name() string {
	return Name
}

func (v *V2rayServer) Start(_ adapter.StartStage) error {
	return nil
}

func (v *V2rayServer) Close() error {
	return nil
}

func (v *V2rayServer) StatsService() adapter.ConnectionTracker {
	return v.statsService
}
