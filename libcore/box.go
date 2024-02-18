package libcore

import (
	"context"
	"fmt"
	"io"
	"log"
	"strings"
	"time"

	"libcore/api"
	"libcore/device"
	"libcore/protectserver"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/conntrack"
	"github.com/sagernet/sing-box/common/urltest"
	_ "github.com/sagernet/sing-box/include"
	boxlog "github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/outbound"
	E "github.com/sagernet/sing/common/exceptions"
	N "github.com/sagernet/sing/common/network"
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

	v2api        *api.SbV2rayServer
	selector     *outbound.Selector
	pauseManager pause.Manager
	servicePauseFields
}

func NewSingBoxInstance(config string, forTest bool) (b *BoxInstance, err error) {
	defer device.DeferPanicToError("NewSingBoxInstance", func(err_ error) { err = err_ })

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
	defer device.DeferPanicToError("box.Start", func(err_ error) { err = err_ })

	if b.state == 0 {
		b.state = 1
		return b.Box.Start()
	}
	return E.New("already started")
}

const closeTimeout = time.Second * 2

func (b *BoxInstance) Close() (err error) {
	defer device.DeferPanicToError("BoxInstance.Close", func(err_ error) { err = err_ })

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
		defer device.DeferPanicToError("box.Close", func(err_ error) { err = err_ })
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
	b.v2api = api.NewSbV2rayServer(option.V2RayStatsServiceOptions{
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

func UrlTest(i *BoxInstance, link string, timeout int32) (latency int32, err error) {
	defer device.DeferPanicToError("box.UrlTest", func(err_ error) { err = err_ })

	var router adapter.Router
	if i == nil {
		// test current
		router = mainInstance.Router()
	} else {
		router = i.Router()
	}

	defOutbound, err := router.DefaultOutbound(N.NetworkTCP)
	if err != nil {
		return 0, E.Cause(err, "find default outbound")
	}

	ctx, cancel := context.WithTimeout(context.Background(), time.Duration(timeout)*time.Millisecond)
	defer cancel()

	chLatency := make(chan uint16, 1)
	go func() {
		var t uint16
		t, err = urltest.URLTest(ctx, link, defOutbound)
		chLatency <- t
	}()
	select {
	case <-ctx.Done():
		return 0, E.New("Url test timeout")
	case t := <-chLatency:
		if err != nil {
			return 0, err
		}
		return int32(t), nil
	}
}

var protectCloser io.Closer

func goServeProtect(start bool) {
	if protectCloser != nil {
		protectCloser.Close()
		protectCloser = nil
	}
	if start {
		protectCloser = protectserver.ServeProtect("protect_path", 0, func(fd int) {
			intfBox.AutoDetectInterfaceControl(int32(fd))
		})
	}
}
