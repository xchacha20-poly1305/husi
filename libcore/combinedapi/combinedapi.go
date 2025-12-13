// Package combinedapi combines V2Ray API and Clash API,
// but not provides public gRPC or HTTP service.
package combinedapi

import (
	"context"
	"net"
	"strings"

	"libcore/combinedapi/trafficcontrol"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/urltest"
	"github.com/sagernet/sing-box/experimental"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/common/observable"
	"github.com/sagernet/sing/service"
)

var _ adapter.ClashServer = (*CombinedAPI)(nil)

func init() {
	experimental.RegisterClashServerConstructor(New)
}

type CombinedAPI struct {
	ctx            context.Context
	outbound       adapter.OutboundManager
	logger         log.Logger
	trafficManager *trafficcontrol.Manager
	mode           string
	modeList       []string
	modeUpdateHook *observable.Subscriber[struct{}]
	urlTestHistory adapter.URLTestHistoryStorage
}

func New(ctx context.Context, logFactory log.ObservableFactory, options option.ClashAPIOptions) (adapter.ClashServer, error) {
	//goland:noinspection GoDeprecation
	//nolint:staticcheck
	if options.StoreMode || options.StoreSelected || options.StoreFakeIP || options.CacheFile != "" || options.CacheID != "" {
		return nil, E.New("cache_file and related fields in Clash API is deprecated in sing-box 1.8.0, use experimental.cache_file instead.")
	}
	c := &CombinedAPI{
		ctx:            ctx,
		outbound:       service.FromContext[adapter.OutboundManager](ctx),
		logger:         logFactory.NewLogger(Name),
		trafficManager: trafficcontrol.NewManager(),
		modeList:       options.ModeList,
	}
	c.urlTestHistory = service.FromContext[adapter.URLTestHistoryStorage](ctx)
	if c.urlTestHistory == nil {
		c.urlTestHistory = urltest.NewHistoryStorage()
	}
	var defaultMode string
	if options.DefaultMode == "" {
		defaultMode = ModeRule
	} else {
		defaultMode = options.DefaultMode
	}
	if !common.Contains(c.modeList, defaultMode) {
		c.modeList = append([]string{defaultMode}, c.modeList...)
	}
	c.mode = defaultMode
	return c, nil
}

const (
	Name = "Combined-API"

	ModeRule = "Rule" // Default mode name.
)

func (c *CombinedAPI) Name() string {
	return Name
}

func (c *CombinedAPI) Start(stage adapter.StartStage) error {
	switch stage {
	case adapter.StartStateStart:
		cacheFile := service.FromContext[adapter.CacheFile](c.ctx)
		if cacheFile != nil {
			mode := cacheFile.LoadMode()
			if common.Any(c.modeList, func(it string) bool {
				return strings.EqualFold(it, mode)
			}) {
				c.mode = mode
			}
		}
	default:
	}
	return nil
}

func (c *CombinedAPI) Close() error {
	return common.Close(c.trafficManager, c.urlTestHistory)
}

func (c *CombinedAPI) RoutedConnection(ctx context.Context, conn net.Conn, metadata adapter.InboundContext, matchedRule adapter.Rule, matchOutbound adapter.Outbound) net.Conn {
	return trafficcontrol.NewTCPTracker(conn, c.trafficManager, metadata, c.outbound, matchedRule, matchOutbound)
}

func (c *CombinedAPI) RoutedPacketConnection(ctx context.Context, conn N.PacketConn, metadata adapter.InboundContext, matchedRule adapter.Rule, matchOutbound adapter.Outbound) N.PacketConn {
	return trafficcontrol.NewUDPTracker(conn, c.trafficManager, metadata, c.outbound, matchedRule, matchOutbound)
}

func (c *CombinedAPI) Mode() string {
	return c.mode
}

func (c *CombinedAPI) SetMode(newMode string) {
	if !common.Contains(c.modeList, newMode) {
		newMode = common.Find(c.modeList, func(it string) bool {
			return strings.EqualFold(it, newMode)
		})
	}
	if !common.Contains(c.modeList, newMode) {
		return
	}
	if newMode == c.mode {
		return
	}
	c.mode = newMode
	if c.modeUpdateHook != nil {
		c.modeUpdateHook.Emit(struct{}{})
	}
	cacheFile := service.FromContext[adapter.CacheFile](c.ctx)
	if cacheFile != nil {
		err := cacheFile.StoreMode(newMode)
		if err != nil {
			c.logger.Error(E.Cause(err, "save mode"))
		}
	}
	c.logger.Info("updated mode: ", newMode)
}

func (c *CombinedAPI) SetModeUpdateHook(hook *observable.Subscriber[struct{}]) {
	c.modeUpdateHook = hook
}

func (c *CombinedAPI) ModeList() []string {
	return c.modeList
}

func (c *CombinedAPI) TrafficManager() *trafficcontrol.Manager {
	return c.trafficManager
}

func (c *CombinedAPI) HistoryStorage() adapter.URLTestHistoryStorage {
	return c.urlTestHistory
}

func (c *CombinedAPI) QueryStats(name string, isUpload bool) int64 {
	return c.trafficManager.QueryStats(name, isUpload)
}
