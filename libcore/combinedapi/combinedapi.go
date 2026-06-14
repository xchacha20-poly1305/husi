// Package combinedapi provides Husi's in-process Clash API shim.
package combinedapi

import (
	"context"
	"strings"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/compatible"
	"github.com/sagernet/sing-box/experimental"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/observable"
	"github.com/sagernet/sing/service"
)

var _ adapter.ClashServer = (*CombinedAPI)(nil)

func init() {
	experimental.RegisterClashServerConstructor(New)
}

type CombinedAPI struct {
	ctx             context.Context
	logger          log.Logger
	dnsRouter       adapter.DNSRouter
	mode            string
	modeList        []string
	modeUpdateHooks compatible.Map[*observable.Subscriber[struct{}], struct{}]
}

func New(ctx context.Context, logFactory log.ObservableFactory, options option.ClashAPIOptions) (adapter.ClashServer, error) {
	c := &CombinedAPI{
		ctx:       ctx,
		logger:    logFactory.NewLogger(Name),
		dnsRouter: service.FromContext[adapter.DNSRouter](ctx),
		modeList:  options.ModeList,
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
	return nil
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
	c.modeUpdateHooks.Range(func(hook *observable.Subscriber[struct{}], _ struct{}) bool {
		hook.Emit(struct{}{})
		return true
	})
	if c.dnsRouter != nil {
		c.dnsRouter.ClearCache()
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

func (c *CombinedAPI) AddModeUpdateHook(hook *observable.Subscriber[struct{}]) {
	c.modeUpdateHooks.Store(hook, struct{}{})
}

func (c *CombinedAPI) DeleteModeUpdateHook(hook *observable.Subscriber[struct{}]) {
	c.modeUpdateHooks.Delete(hook)
}

func (c *CombinedAPI) ModeList() []string {
	return c.modeList
}
