package libcore

import (
	"context"
	"time"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/conntrack"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/clashapi"
	"github.com/sagernet/sing-box/experimental/deprecated"
	"github.com/sagernet/sing-box/experimental/libbox/platform"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/protocol/group"
	"github.com/sagernet/sing/common/atomic"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"

	"libcore/protect"
	"libcore/trackerchain"
	"libcore/v2rayapilite"
)

func ResetAllConnections() {
	conntrack.Close()
	log.Debug("Reset system connections done.")
}

// boxState is sing-box state
type boxState = uint8

const (
	boxStateNeverStarted boxState = iota
	boxStateRunning
	boxStateClosed
)

type BoxInstance struct {
	ctx    context.Context
	cancel context.CancelFunc
	*box.Box
	forTest bool
	state   atomic.TypedValue[boxState]

	selector          *group.Selector
	v2ray             *v2rayapilite.V2rayServer
	clash             *clashapi.Server
	protect           *protect.Protect
	clashModeHook     chan struct{}
	platformInterface PlatformInterface

	pauseManager pause.Manager
	servicePauseFields
}

// NewBoxInstance creates a new BoxInstance.
// If platformInterface is nil, it will use test mode.
func NewBoxInstance(config string, platformInterface PlatformInterface) (b *BoxInstance, err error) {
	forTest := platformInterface == nil
	defer catchPanic("NewSingBoxInstance", func(panicErr error) { err = panicErr })

	ctx := box.Context(context.Background(), include.InboundRegistry(), include.OutboundRegistry(), include.EndpointRegistry())
	options, err := parseConfig(ctx, config)
	if err != nil {
		return nil, err
	}

	ctx, cancel := context.WithCancel(ctx)
	ctx = pause.WithDefaultManager(ctx)
	var platformLogWriter log.PlatformWriter
	if !forTest {
		interfaceWrapper := &boxPlatformInterfaceWrapper{
			useProcFS: platformInterface.UseProcFS(),
			iif:       platformInterface,
		}
		service.MustRegister[platform.Interface](ctx, interfaceWrapper)
		service.MustRegister[deprecated.Manager](ctx, deprecated.NewStderrManager(log.StdLogger()))

		// If set PlatformLogWrapper, box will set something about cache file,
		// which will panic with simple configuration (when URL test).
		platformLogWriter = platformLogWrapper
	} else {
		// Make the behavior like platform.
		service.MustRegister[platform.Interface](ctx, platformInterfaceStub{})
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

	b = &BoxInstance{
		ctx:               ctx,
		Box:               instance,
		forTest:           forTest,
		cancel:            cancel,
		platformInterface: platformInterface,
		pauseManager:      service.FromContext[pause.Manager](ctx),
	}

	if !forTest {
		// selector
		if proxy, haveProxyOutbound := b.Box.Outbound().Outbound("proxy"); haveProxyOutbound {
			if selector, isSelector := proxy.(*group.Selector); isSelector {
				b.selector = selector
			}
		}

		// Protect
		b.protect, err = protect.New(ctx, log.StdLogger(), ProtectPath, func(fd int) error {
			return platformInterface.AutoDetectInterfaceControl(int32(fd))
		})
		if err != nil {
			log.WarnContext(ctx, "create protect service: ", err)
		}

		// API
		b.clash = service.FromContext[adapter.ClashServer](b.ctx).(*clashapi.Server)
		b.v2ray = service.FromContext[adapter.V2RayServer](b.ctx).(*v2rayapilite.V2rayServer)
		b.Router().SetTracker(trackerchain.New(b.v2ray.StatsService(), b.clash))

	}

	return b, nil
}

func (b *BoxInstance) Start() (err error) {
	defer catchPanic("box.Start", func(panicErr error) { err = panicErr })

	if b.state.Load() != boxStateNeverStarted {
		return E.New("box already started")
	}
	b.state.Store(boxStateRunning)
	err = b.Box.Start()
	if err != nil {
		return err
	}

	if b.protect != nil {
		if err := b.protect.Start(); err != nil {
			log.Warn(E.Cause(err))
		}
	}
	if b.selector != nil {
		go b.listenSelectorChange(b.ctx, b.platformInterface.SelectorCallback)
	}

	return nil
}

func (b *BoxInstance) Close() (err error) {
	return b.CloseTimeout(C.FatalStopTimeout)
}

func (b *BoxInstance) CloseTimeout(timeout time.Duration) (err error) {
	defer catchPanic("BoxInstance.Close", func(panicErr error) { err = panicErr })

	// no double close
	if b.state.Swap(boxStateClosed) == boxStateClosed {
		return nil
	}

	if b.protect != nil {
		_ = b.protect.Close()
	}

	if b.clashModeHook != nil {
		select {
		case <-b.clashModeHook:
			// closed
		default:
			b.clash.SetModeUpdateHook(nil)
			close(b.clashModeHook)
		}
	}

	done := make(chan struct{})
	start := time.Now()
	go func(done chan<- struct{}) {
		defer catchPanic("box.Close", func(panicErr error) { err = panicErr })
		b.cancel()
		_ = b.Box.Close()
		close(done)
	}(done)
	select {
	case <-time.After(timeout):
		return E.New("sing-box did not close in time")
	case <-done:
		if b.forTest {
			return nil
		}
		log.Info("sing-box closed in ", F.Seconds(time.Since(start).Seconds()), " s.")
		return nil
	}
}

func (b *BoxInstance) NeedWIFIState() bool {
	return b.Box.Router().NeedWIFIState()
}

func (b *BoxInstance) QueryStats(tag, direct string) int64 {
	return b.v2ray.QueryStats("outbound>>>" + tag + ">>>traffic>>>" + direct)
}

func (b *BoxInstance) SelectOutbound(tag string) (ok bool) {
	if b.selector != nil {
		return b.selector.SelectOutbound(tag)
	}
	return false
}
