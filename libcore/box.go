package libcore

import (
	"context"
	"runtime/debug"
	"time"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/common/urltest"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/deprecated"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	F "github.com/sagernet/sing/common/format"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"
)

// boxInstance is the trimmed forTest path used by StandaloneURLTest. Production
// instance lifecycle lives in daemon.StartedService (coresvc).
type boxInstance struct {
	ctx    context.Context
	cancel context.CancelFunc
	*box.Box
	forTest bool

	platformInterface PlatformInterface
	urlTestHistory    *urltest.HistoryStorage
	pauseManager      pause.Manager
}

// newBoxInstance creates a boxInstance. forTest must be true for the surviving
// StandaloneURLTest path (no platform log writer, no combinedapi assertion).
func newBoxInstance(config string, platformInterface PlatformInterface, forTest bool) (b *boxInstance, err error) {
	defer catchPanic("NewSingBoxInstance", func(panicErr error) { err = panicErr })

	ctx := baseContext(platformInterface)
	options, err := parseConfig(ctx, config)
	if err != nil {
		return nil, err
	}

	ctx, cancel := context.WithCancel(ctx)
	ctx = pause.WithDefaultManager(ctx)
	registerPlatformInterface(ctx, platformInterface, forTest)

	var platformLogWriter log.PlatformWriter
	if !forTest {
		service.MustRegister[deprecated.Manager](ctx, deprecated.NewStderrManager(log.StdLogger()))
		platformLogWriter = fileLogSink()
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
		urlTestHistory:    service.PtrFromContext[urltest.HistoryStorage](ctx),
	}
	return b, nil
}

func (b *boxInstance) Start() (err error) {
	defer catchPanic("box.Start", func(panicErr error) { err = panicErr })

	err = b.Box.Start()
	if err != nil {
		return err
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
