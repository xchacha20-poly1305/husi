package libcore

import (
	"context"
	"runtime/debug"
	"time"

	"libcore/combinedapi"
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
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"

	"github.com/xchacha20-poly1305/anchor/anchorservice"
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
	anchor             *anchorservice.Anchor

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

		// Anchor
		socksPort, dnsPort := sharedPublicPort(options.Inbounds)
		if socksPort > 0 {
			b.anchor, err = b.createAnchor(socksPort, dnsPort)
			if err != nil {
				log.WarnContext(b.ctx, "create anchor: ", err)
			}
		}
	}

	return b, nil
}

func (b *boxInstance) Start() (err error) {
	defer catchPanic("box.Start", func(panicErr error) { err = panicErr })

	err = b.Box.Start()
	if err != nil {
		return err
	}

	if b.protect != nil {
		// Never return error
		_ = b.protect.Start()
	}
	if b.anchor != nil {
		err = b.anchor.Start()
		if err != nil {
			return E.Cause(err, "start anchor service")
		}
	}

	if b.connectionObserver != nil {
		go b.connectionObserver.run(b.ctx)
	}

	if !b.forTest {
		debug.FreeOSMemory()
	}

	return nil
}

func (b *boxInstance) Close() (err error) {
	return b.CloseTimeout(C.FatalStopTimeout)
}

func (b *boxInstance) CloseTimeout(timeout time.Duration) (err error) {
	defer catchPanic("boxInstance.Close", func(panicErr error) { err = panicErr })

	_ = common.Close(
		common.PtrOrNil(b.protect),
		common.PtrOrNil(b.anchor),
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
	return b.anchor != nil || b.Network().NeedWIFIState()
}

func (b *boxInstance) resetNetwork() {
	b.Network().ResetNetwork()
}
