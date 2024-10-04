package libcore

import (
	"context"
	"time"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/common/conntrack"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/clashapi"
	"github.com/sagernet/sing-box/experimental/libbox/platform"
	_ "github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/outbound"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/atomic"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"

	"libcore/protect"
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
	*box.Box
	forTest bool
	cancel  context.CancelFunc
	state   atomic.TypedValue[boxState]

	// services are BoxInstance' extra services.
	services          []any
	selector          *outbound.Selector
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

	options, err := parseConfig(config)
	if err != nil {
		return nil, err
	}

	// create box
	ctx, cancel := context.WithCancel(context.Background())
	ctx = pause.WithDefaultManager(ctx)
	var interfaceWrapper platform.Interface
	if forTest {
		interfaceWrapper = platformInterfaceStub{}
	} else {
		interfaceWrapper = &boxPlatformInterfaceWrapper{
			useProcFS: platformInterface.UseProcFS(),
			iif:       platformInterface,
		}
	}
	boxOption := box.Options{
		Options:           options,
		Context:           ctx,
		PlatformInterface: interfaceWrapper,
	}

	// If set PlatformLogWrapper, box will set something about cache file,
	// which will panic with simple configuration (when URL test).
	if !forTest {
		boxOption.PlatformLogWriter = platformLogWrapper
	}

	instance, err := box.New(boxOption)
	if err != nil {
		cancel()
		return nil, E.Cause(err, "create service")
	}

	b = &BoxInstance{
		Box:               instance,
		forTest:           forTest,
		cancel:            cancel,
		platformInterface: platformInterface,
		pauseManager:      service.FromContext[pause.Manager](ctx),
	}

	if !forTest {
		// selector
		if proxy, haveProxyOutbound := b.Box.Router().Outbound("proxy"); haveProxyOutbound {
			if selector, isSelector := proxy.(*outbound.Selector); isSelector {
				b.selector = selector
			}
		}

		// Protect
		protectServer, err := protect.New(ctx, log.StdLogger(), ProtectPath, func(fd int) error {
			return platformInterface.AutoDetectInterfaceControl(int32(fd))
		})
		if err != nil {
			log.WarnContext(ctx, "create protect service: ", err)
		} else {
			b.services = append(b.services, protectServer)
		}
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

	if !b.forTest {
		for i, extraService := range b.services {
			if starter, isStarter := extraService.(interface {
				Start() error
			}); isStarter {
				err := starter.Start()
				if err != nil {
					log.Warn("starting extra service [", i, "]: ", err)
				}
			}
		}
		if b.selector != nil {
			oldCancel := b.cancel
			ctx, cancel := context.WithCancel(context.Background())
			b.cancel = func() {
				oldCancel()
				cancel()
			}
			go b.listenSelectorChange(ctx, b.platformInterface.SelectorCallback)
		}
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

	_ = common.Close(b.services)

	if b.clashModeHook != nil {
		select {
		case <-b.clashModeHook:
			// closed
		default:
			b.Router().ClashServer().(*clashapi.Server).SetModeUpdateHook(nil)
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
	statsGetter := b.Router().V2RayServer().(v2rayapilite.StatsGetter)
	if statsGetter == nil {
		return 0
	}
	return statsGetter.QueryStats("outbound>>>" + tag + ">>>traffic>>>" + direct)
}

func (b *BoxInstance) SelectOutbound(tag string) (ok bool) {
	if b.selector != nil {
		return b.selector.SelectOutbound(tag)
	}
	return false
}
