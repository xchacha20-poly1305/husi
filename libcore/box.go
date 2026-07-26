package libcore

import (
	"context"
	"runtime/debug"
	"time"

	"libcore/combinedapi"
	"libcore/plugin/anchor"
	"libcore/protect"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/trafficcontrol"
	"github.com/sagernet/sing-box/common/urltest"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/deprecated"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/common/x/list"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"

	"go4.org/netipx"
)

type boxInstance struct {
	ctx    context.Context
	cancel context.CancelFunc
	*box.Box
	forTest bool

	platformInterface  PlatformInterface
	protect            *protect.Service
	api                *combinedapi.CombinedAPI
	trafficManager     *trafficcontrol.Manager
	urlTestHistory     *urltest.HistoryStorage
	connectionObserver *connectionObserver

	pauseManager pause.Manager
}

// newBoxInstance creates a new boxInstance.
func newBoxInstance(config string, platformInterface PlatformInterface, forTest bool) (b *boxInstance, err error) {
	defer catchPanic("NewSingBoxInstance", func(panicErr error) { err = panicErr })

	ctx := baseContext(platformInterface)
	options, err := parseConfig(ctx, config)
	if err != nil {
		return nil, err
	}

	ctx, cancel := context.WithCancel(ctx)
	ctx = pause.WithDefaultManager(ctx)
	var platformLogWriter log.PlatformWriter
	registerPlatformInterface(ctx, platformInterface, forTest)

	if !forTest {
		service.MustRegister[deprecated.Manager](ctx, deprecated.NewStderrManager(log.StdLogger()))
		// If set PlatformLogWrapper, box will set something about cache file,
		// which will panic with simple configuration (when URL test).
		platformLogWriter = platformLogWrapper
	}

	boxOption := box.Options{
		Options:           options,
		Context:           ctx,
		PlatformLogWriter: platformLogWriter,
	}

	instance, err := box.New(boxOption)
	if err != nil {
		cancel()
		return nil, E.Cause(err, "create service")
	}

	b = &boxInstance{
		ctx:               ctx,
		Box:               instance,
		forTest:           forTest,
		cancel:            cancel,
		platformInterface: platformInterface,
		pauseManager:      service.FromContext[pause.Manager](ctx),
	}

	if !forTest {
		// Protect
		buildProtectService(b, ctx, platformInterface)

		// API
		b.api = service.FromContext[adapter.ClashServer](ctx).(*combinedapi.CombinedAPI)
		b.trafficManager = service.PtrFromContext[trafficcontrol.Manager](ctx)
		b.urlTestHistory = service.PtrFromContext[urltest.HistoryStorage](ctx)
		if b.trafficManager != nil {
			b.connectionObserver = newConnectionObserver(b.trafficManager)
		}

	}

	return b, nil
}

func (b *boxInstance) Start() (err error) {
	defer catchPanic("box.Start", func(panicErr error) { err = panicErr })

	// Workaround to set `needWIFIState` for anchor
	serviceManager := service.FromContext[adapter.ServiceManager](b.ctx)
	if common.Any(serviceManager.Services(), func(it adapter.Service) bool {
		_, isAnchorService := it.(*anchor.Service)
		return isAnchorService
	}) {
		b.Box.Network().Initialize([]adapter.RuleSet{fakeWifiRuleSet{}})
	}

	err = b.Box.Start()
	if err != nil {
		return err
	}

	if b.protect != nil {
		// Never return error
		_ = b.protect.Start()
	}
	if b.connectionObserver != nil {
		go b.connectionObserver.run(b.ctx)
	}

	if !b.forTest {
		debug.FreeOSMemory()
	}

	return nil
}

var _ adapter.RuleSet = fakeWifiRuleSet{}

type fakeWifiRuleSet struct{}

func (f fakeWifiRuleSet) Name() string {
	return f.String()
}

func (f fakeWifiRuleSet) StartContext(ctx context.Context, startContext *adapter.HTTPStartContext) error {
	return nil
}

func (f fakeWifiRuleSet) Metadata() adapter.RuleSetMetadata {
	return adapter.RuleSetMetadata{
		ContainsWIFIRule: true,
	}
}

func (f fakeWifiRuleSet) ExtractIPSet() []*netipx.IPSet {
	return nil
}

func (f fakeWifiRuleSet) IncRef() {
}

func (f fakeWifiRuleSet) DecRef() {
}

func (f fakeWifiRuleSet) Cleanup() {
}

func (f fakeWifiRuleSet) RegisterCallback(callback adapter.RuleSetUpdateCallback) *list.Element[adapter.RuleSetUpdateCallback] {
	return nil
}

func (f fakeWifiRuleSet) UnregisterCallback(element *list.Element[adapter.RuleSetUpdateCallback]) {
}

func (f fakeWifiRuleSet) Close() error {
	return nil
}

func (f fakeWifiRuleSet) Match(metadata *adapter.InboundContext) bool {
	return false
}

func (f fakeWifiRuleSet) String() string {
	return "fakeWifiRuleSet"
}

func (b *boxInstance) Close() (err error) {
	return b.CloseTimeout(C.FatalStopTimeout)
}

func (b *boxInstance) CloseTimeout(timeout time.Duration) (err error) {
	defer catchPanic("boxInstance.Close", func(panicErr error) { err = panicErr })

	_ = common.Close(
		common.PtrOrNil(b.protect),
	)

	done := make(chan error, 1)
	start := time.Now()
	go func() {
		defer catchPanic("box.Close", func(panicErr error) { done <- panicErr })
		b.cancel()
		done <- b.Box.Close()
	}()
	select {
	case <-time.After(timeout):
		return E.New("sing-box did not close in time")
	case err = <-done:
		if !b.forTest {
			log.Info("sing-box closed in ", F.Seconds(time.Since(start).Seconds()), " s.")
		}
		return
	}
}

func (b *boxInstance) NeedWIFIState() bool {
	return b.Network().NeedWIFIState()
}

func (b *boxInstance) resetNetwork() {
	b.Network().ResetNetwork()
}
