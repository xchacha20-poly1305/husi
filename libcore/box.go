package libcore

import (
	"context"
	"fmt"
	"io"
	"log"
	"strings"
	"time"

	"libcore/protectserver"
	"libcore/v2rayapilite"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/common/conntrack"
	_ "github.com/sagernet/sing-box/include"
	boxlog "github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/outbound"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"
)

var mainInstance *BoxInstance

func ResetAllConnections(system bool) {
	if system {
		conntrack.Close()
		log.Println("[Debug] Reset system connections done.")
	}
}

type BoxInstance struct {
	*box.Box
	cancel context.CancelFunc

	// state is sing-box state
	// 0: never started
	// 1: running
	// 2: closed
	state int

	v2api        *v2rayapilite.V2RayServerLite
	selector     *outbound.Selector
	pauseManager pause.Manager
	servicePauseFields
}

func NewSingBoxInstance(config string, forTest bool) (b *BoxInstance, err error) {
	defer catchPanic("NewSingBoxInstance", func(panicErr error) { err = panicErr })

	// parse options
	var options option.Options
	err = options.UnmarshalJSON([]byte(config))
	if err != nil {
		return nil, E.Cause(err, "decode config")
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
	// If set platformLogWrapper, box will set something about cache file,
	// which will panic with simple configuration.
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

	// TODO: remove
	// selector
	proxy, outboundHasProxy := b.Router().Outbound("proxy")
	if outboundHasProxy {
		selector, enabledSelector := proxy.(*outbound.Selector)
		if enabledSelector {
			b.selector = selector
		}
	}

	return b, nil
}

func (b *BoxInstance) Start() (err error) {
	defer catchPanic("box.Start", func(panicErr error) { err = panicErr })

	if b.state == 0 {
		b.state = 1
		return b.Box.Start()
	}
	return E.New("already started")
}

const closeTimeout = time.Second * 2

func (b *BoxInstance) Close() (err error) {
	defer catchPanic("BoxInstance.Close", func(panicErr error) { err = panicErr })

	// no double close
	if b.state == 2 {
		return nil
	}
	b.state = 2

	// clear main instance
	if mainInstance == b {
		mainInstance = nil
		goServeProtect(false)
	}

	// close box
	chClosed := make(chan struct{})
	ctx, cancel := context.WithTimeout(context.Background(), closeTimeout)
	defer cancel()
	start := time.Now()
	go func() {
		defer catchPanic("box.Close", func(panicErr error) { err = panicErr })
		b.cancel()
		err = b.Box.Close()
		close(chClosed)
	}()
	select {
	case <-ctx.Done():
		boxlog.Warn("Closing sing-box takes longer than expected.")
	case <-chClosed:
		boxlog.Info(fmt.Sprintf("sing-box closed in %d ms.", time.Since(start).Milliseconds()))
	}

	return err
}

func (b *BoxInstance) NeedWIFIState() bool {
	return b.Router().NeedWIFIState()
}

func (b *BoxInstance) SetAsMain() {
	mainInstance = b
	goServeProtect(true)
}

func (b *BoxInstance) SetConnectionPoolEnabled(enable bool) {
	// TODO api
}

func (b *BoxInstance) SetV2rayStats(outbounds string) {
	b.v2api = v2rayapilite.NewSbV2rayServer(option.V2RayStatsServiceOptions{
		Enabled:   true,
		Outbounds: strings.Split(outbounds, "\n"),
	})
	b.Box.Router().SetV2RayServer(b.v2api)
}

func (b *BoxInstance) QueryStats(tag, direct string) int64 {
	if b.v2api == nil {
		return 0
	}
	return b.v2api.QueryStats(fmt.Sprintf("outbound>>>%s>>>traffic>>>%s", tag, direct))
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
		protectCloser = protectserver.ServeProtect("protect_path", 0, func(fd int) {
			_ = intfBox.AutoDetectInterfaceControl(int32(fd))
		})
	}
}
