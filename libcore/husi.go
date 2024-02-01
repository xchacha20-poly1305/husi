package libcore

import (
	"log"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	_ "unsafe"

	"github.com/sagernet/sing-box/nekoutils"
	"github.com/xchacha20-poly1305/dun/dunbox"
	"libcore/device"
)

//go:linkname resourcePaths github.com/sagernet/sing-box/constant.resourcePaths
var resourcePaths []string

func NekoLogPrintln(s string) {
	log.Println(s)
}

func NekoLogClear() {
	platformLogWrapper.truncate()
}

func ForceGc() {
	go runtime.GC()
}

func InitCore(process, cachePath, internalAssets, externalAssets string,
	maxLogSizeKb int32, logEnable bool,
	if1 NB4AInterface, if2 BoxPlatformInterface,
	enabledCazilla bool,
) {
	defer device.DeferPanicToError("InitCore", func(err error) { log.Println(err) })
	isBgProcess := strings.HasSuffix(process, ":bg")

	intfNB4A = if1
	intfBox = if2
	useProcfs = intfBox.UseProcFS()

	// Working dir
	tmp := filepath.Join(cachePath, "../no_backup")
	os.MkdirAll(tmp, 0755)
	os.Chdir(tmp)

	// sing-box fs
	resourcePaths = append(resourcePaths, externalAssets)

	// Set up log
	if maxLogSizeKb < 50 {
		maxLogSizeKb = 50
	}
	logWriterDisable = !logEnable
	truncateOnStart = isBgProcess
	setupLog(int64(maxLogSizeKb)*1024, filepath.Join(cachePath, "neko.log"))
	dunbox.DisableColor()

	// nekoutils
	nekoutils.Selector_OnProxySelected = intfNB4A.Selector_OnProxySelected

	// Set up some component
	go func() {
		defer device.DeferPanicToError("InitCore-go", func(err error) { log.Println(err) })
		device.GoDebug(process)

		externalAssetsPath = externalAssets
		internalAssetsPath = internalAssets

		// certs
		pem, _ := os.ReadFile(externalAssetsPath + "ca.pem")
		updateRootCACerts(pem, enabledCazilla)

		// bg
		if isBgProcess {
			extractAssets()
		}
	}()
}
