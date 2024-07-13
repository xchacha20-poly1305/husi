package libcore

import (
	"context"
	"fmt"
	"io"
	"time"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/common/conntrack"
	C "github.com/sagernet/sing-box/constant"
	_ "github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/outbound"
	"github.com/sagernet/sing/common/atomic"
	E "github.com/sagernet/sing/common/exceptions"
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

	state          atomic.TypedValue[boxState]
	isMainInstance bool

	selector *outbound.Selector

	pauseManager pause.Manager
	servicePauseFields
}

func NewSingBoxInstance(config string, forTest bool) (b *BoxInstance, err error) {
	defer catchPanic("NewSingBoxInstance", func(panicErr error) { err = panicErr })

	options, err := parseConfig(config)
	if err != nil {
		return nil, err
	}

	// create box
	ctx, cancel := context.WithCancel(context.Background())
	ctx = pause.WithDefaultManager(ctx)
	platformWrapper := &boxPlatformInterfaceWrapper{}
	boxOption := box.Options{
		Options:           options,
		Context:           ctx,
		PlatformInterface: platformWrapper,
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
		Box:          instance,
		cancel:       cancel,
		pauseManager: service.FromContext[pause.Manager](ctx),
	}

	// selector
	if proxy, haveProxyOutbound := b.Box.Router().Outbound("proxy"); haveProxyOutbound {
		if selector, isSelector := proxy.(*outbound.Selector); isSelector {
			b.selector = selector
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
		}(b, intfGUI.SelectorCallback)
		return b.Box.Start()
	}
	return E.New("already started")
}

func (b *BoxInstance) Close() (err error) {
	defer catchPanic("BoxInstance.Close", func(panicErr error) { err = panicErr })

	// no double close
	if b.state.Load() == boxStateClosed {
		return nil
	}
	b.state.Store(boxStateClosed)

	if b.isMainInstance {
		goServeProtect(false)
	}

	// close box
	chClosed := make(chan struct{})
	ctx, cancel := context.WithTimeout(context.Background(), C.StopTimeout)
	defer cancel()
	start := time.Now()
	go func() {
		defer catchPanic("box.Close", func(panicErr error) { err = panicErr })
		b.cancel()
		_ = b.Box.Close()
		close(chClosed)
	}()
	select {
	case <-ctx.Done():
		return E.New("sing-box did not close in time")
	case <-chClosed:
		log.Info(fmt.Sprintf("sing-box closed in %d ms.", time.Since(start).Milliseconds()))
		return nil
	}
}

func (b *BoxInstance) NeedWIFIState() bool {
	return b.Box.Router().NeedWIFIState()
}

func (b *BoxInstance) SetAsMain() {
	b.isMainInstance = true
	goServeProtect(true)
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

var protectCloser io.Closer

func goServeProtect(start bool) {
	if protectCloser != nil {
		_ = protectCloser.Close()
		protectCloser = nil
	}

	if start {
		protectCloser = protect.ServerProtect(ProtectPath, func(fd int) error {
			if intfBox == nil {
				return E.New("not init intfBox")
			}
			return intfBox.AutoDetectInterfaceControl(int32(fd))
		})
	}
}
