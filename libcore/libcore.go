package libcore

import (
	"log"
	"os"
	"path/filepath"
	"strings"
	_ "unsafe"
)

const ProtectPath = "protect_path"

func Kill() {
	os.Exit(0)
}

func InitCore(process, cachePath, internalAssets, externalAssets string,
	maxLogSizeKb int32, logEnable bool,
	useOfficialAssets bool,
) {
	defer catchPanic("InitCore", func(panicErr error) { log.Println(panicErr) })
	isBgProcess := strings.HasSuffix(process, ":bg")

	workDir := filepath.Join(cachePath, "../no_backup")
	_ = os.MkdirAll(workDir, 0o755)
	_ = os.Chdir(workDir)

	// Set up log
	if maxLogSizeKb < 50 {
		maxLogSizeKb = 50
	}
	_ = setupLog(int64(maxLogSizeKb)*1024, filepath.Join(externalAssets, "stderr.log"), logEnable, isBgProcess)

	// Set up some component
	go func() {
		defer catchPanic("InitCore-go", func(panicErr error) { log.Println(panicErr) })
		GoDebug(process)

		externalAssetsPath = externalAssets
		internalAssetsPath = internalAssets

		// bg
		if isBgProcess {
			extractAssets(useOfficialAssets)
		}
	}()
}
