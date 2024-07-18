package libcore

import (
	"context"
	"io"
	"time"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/common/conntrack"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/libbox/platform"
	_ "github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/outbound"
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

	cancel context.CancelFunc

	state atomic.TypedValue[boxState]

	protectFun    protect.Protect
	protectCloser io.Closer

	selector         *outbound.Selector
	selectorCallback selectorCallback

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
		Box:    instance,
		cancel: cancel,
		protectFun: func(fd int) error {
			return platformInterface.AutoDetectInterfaceControl(int32(fd))
		},
		pauseManager: service.FromContext[pause.Manager](ctx),
	}

	// selector
	if !forTest {
		if proxy, haveProxyOutbound := b.Box.Router().Outbound("proxy"); haveProxyOutbound {
			if selector, isSelector := proxy.(*outbound.Selector); isSelector {
				b.selector = selector
				b.selectorCallback = platformInterface.SelectorCallback
			}
		}
	}

	return b, nil
}

func (b *BoxInstance) Start() (err error) {
	defer catchPanic("box.Start", func(panicErr error) { err = panicErr })

	if b.state.Load() == boxStateNeverStarted {
		b.state.Store(boxStateRunning)
		defer func(b *BoxInstance, callback selectorCallback) {
			if b.selector != nil {
				boxCancel := b.cancel
				ctx, cancelContext := context.WithCancel(context.Background())
				b.cancel = func() {
					boxCancel()
					cancelContext()
				}
				go b.listenSelectorChange(ctx, callback)
			}
		}(b, b.selectorCallback)
		return b.Box.Start()
	}
	return E.New("box already started")
}

func (b *BoxInstance) Close() (err error) {
	defer catchPanic("BoxInstance.Close", func(panicErr error) { err = panicErr })

	// no double close
	if b.state.Swap(boxStateClosed) == boxStateClosed {
		return nil
	}

	if b.protectCloser != nil {
		_ = b.protectCloser.Close()
	}

	// close box
	done := make(chan struct{})
	ctx, cancel := context.WithTimeout(context.Background(), C.FatalStopTimeout)
	defer cancel()
	start := time.Now()
	go func(done chan<- struct{}) {
		defer catchPanic("box.Close", func(panicErr error) { err = panicErr })
		b.cancel()
		_ = b.Box.Close()
		close(done)
	}(done)
	select {
	case <-ctx.Done():
		return E.New("sing-box did not close in time")
	case <-done:
		log.Info("sing-box closed in ", F.Seconds(time.Since(start).Seconds()), " s.")
		return nil
	}
}

func (b *BoxInstance) NeedWIFIState() bool {
	return b.Box.Router().NeedWIFIState()
}

// SetAsMain starts protect server listening.
func (b *BoxInstance) SetAsMain() {
	b.protectCloser = serveProtect(b.protectFun)
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

func serveProtect(protectFunc protect.Protect) io.Closer {
	return protect.ServerProtect(ProtectPath, func(fd int) error {
		return protectFunc(fd)
	})
}
