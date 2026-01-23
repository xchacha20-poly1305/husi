package libcore

import (
	"context"
	"runtime/debug"
	"time"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/conntrack"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/deprecated"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"

	"github.com/xchacha20-poly1305/anchor/anchorservice"

	"libcore/combinedapi"
	"libcore/protect"
)

func ResetAllConnections() {
	conntrack.Close()
	log.Debug("Reset system connections done.")
}

type boxInstance struct {
	ctx    context.Context
	cancel context.CancelFunc
	*box.Box
	forTest bool

	platformInterface PlatformInterface
	protect           *protect.Service
	api               *combinedapi.CombinedAPI
	anchor            *anchorservice.Anchor

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
	interfaceWrapper := &boxPlatformInterfaceWrapper{
		useProcFS: platformInterface.UseProcFS(),
		iif:       platformInterface,
		forTest:   forTest,
	}
	service.MustRegister[adapter.PlatformInterface](ctx, interfaceWrapper)

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
		b.protect, err = protect.New(log.ContextWithNewID(ctx), logFactory.NewLogger("protect"), ProtectPath, func(fd int) error {
			_ = platformInterface.AutoDetectInterfaceControl(int32(fd))
			return nil
		})
		if err != nil {
			log.WarnContext(ctx, "create protect service: ", err)
		}

		// API
		b.api = service.FromContext[adapter.ClashServer](b.ctx).(*combinedapi.CombinedAPI)

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
	return b.anchor != nil || b.Box.Network().NeedWIFIState()
}

func (b *boxInstance) QueryStats(tag string, isUpload bool) int64 {
	return b.api.QueryStats(tag, isUpload)
}
