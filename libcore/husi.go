package libcore

import (
	"log"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	_ "unsafe"

	"github.com/sagernet/sing-box/nekoutils"
)

//go:linkname resourcePaths github.com/sagernet/sing-box/constant.resourcePaths
var resourcePaths []string

func LogPrintln(s string) {
	log.Println(s)
}

func LogClear() {
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
	defer catchPanic("InitCore", func(panicErr error) { log.Println(panicErr) })
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

	// nekoutils
	nekoutils.Selector_OnProxySelected = intfNB4A.Selector_OnProxySelected

	// Set up some component
	go func() {
		defer catchPanic("InitCore-go", func(panicErr error) { log.Println(panicErr) })
		GoDebug(process)

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
