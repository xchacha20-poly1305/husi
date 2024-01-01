package libcore

import (
	"context"
	"fmt"
	"io"
	"log"
	"runtime"
	"runtime/debug"
	"strings"
	"time"

	_ "github.com/xchacha20-poly1305/dun/distro/all"
	"github.com/xchacha20-poly1305/dun/dunapi"
	"github.com/xchacha20-poly1305/dun/dunbox"
	"libcore/device"

	"github.com/matsuridayo/libneko/protect_server"
	"github.com/matsuridayo/libneko/speedtest"

	"github.com/sagernet/sing-box/common/conntrack"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/outbound"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/service"
	"github.com/sagernet/sing/service/pause"
)

var mainInstance *BoxInstance

// VersionBox
// Get your box version
//
// Format:
//
//	sing-box: {dun_version}
//	{go_version}@{os}/{arch}
//	{tags}
func VersionBox() string {
	version := []string{
		"sing-box: " + dunbox.Version,
		runtime.Version() + "@" + runtime.GOOS + "/" + runtime.GOARCH,
	}

	debugInfo, loaded := debug.ReadBuildInfo()
	if loaded {
		for _, setting := range debugInfo.Settings {
			switch setting.Key {
			case "-tags":
				if setting.Value != "" {
					version = append(version, setting.Value)
					break
				}
			}
		}
	}

	return strings.Join(version, "\n")
}

func ResetAllConnections(system bool) {
	if system {
		conntrack.Close()
		log.Println("[Debug] Reset system connections done")
	}
}

type BoxInstance struct {
	*dunbox.Box
	cancel context.CancelFunc

	// state is sing-box state
	// 0: never started
	// 1: running
	// 2: closed
	state int

	v2api        *dunapi.SbV2rayServer
	selector     *outbound.Selector
	pauseManager pause.Manager

	ForTest bool
}

func NewSingBoxInstance(config string) (b *BoxInstance, err error) {
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
	instance, err := dunbox.New(dunbox.Options{
		Options:           options,
		Context:           ctx,
		PlatformInterface: boxPlatformInterfaceInstance,
	})
	if err != nil {
		cancel()
		return nil, E.Cause(err, "create service")
	}

	b = &BoxInstance{
		Box:          instance,
		cancel:       cancel,
		pauseManager: service.FromContext[pause.Manager](ctx),
	}

	// sing-box platformFormatter
	logPlatformFormatter := instance.GetLogPlatformFormatter()
	logPlatformFormatter.DisableColors = true
	logPlatformFormatter.DisableLineBreak = false

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

func (b *BoxInstance) Close() (err error) {
	defer device.DeferPanicToError("box.Close", func(err_ error) { err = err_ })

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
	b.CloseWithTimeout(b.cancel, time.Second*2, log.Println)

	return nil
}

func (b *BoxInstance) Sleep() {
	b.pauseManager.DevicePause()
	b.Box.Router().ResetNetwork()
}

func (b *BoxInstance) Wake() {
	b.pauseManager.DeviceWake()
}

func (b *BoxInstance) SetAsMain() {
	mainInstance = b
	goServeProtect(true)
}

func (b *BoxInstance) SetConnectionPoolEnabled(enable bool) {
	// TODO api
}

func (b *BoxInstance) SetV2rayStats(outbounds string) {
	b.v2api = dunapi.NewSbV2rayServer(option.V2RayStatsServiceOptions{
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

func (b *BoxInstance) SelectOutbound(tag string) bool {
	if b.selector != nil {
		return b.selector.SelectOutbound(tag)
	}
	return false
}

func UrlTest(i *BoxInstance, link string, timeout int32) (latency int32, err error) {
	defer device.DeferPanicToError("box.UrlTest", func(err_ error) { err = err_ })
	if i == nil {
		// test current
		return speedtest.UrlTest(dunapi.CreateProxyHttpClient(mainInstance.Box), link, timeout)
	}
	return speedtest.UrlTest(dunapi.CreateProxyHttpClient(i.Box), link, timeout)
}

var protectCloser io.Closer

func goServeProtect(start bool) {
	if protectCloser != nil {
		protectCloser.Close()
		protectCloser = nil
	}
	if start {
		protectCloser = protect_server.ServeProtect("protect_path", false, 0, func(fd int) {
			intfBox.AutoDetectInterfaceControl(int32(fd))
		})
	}
}
