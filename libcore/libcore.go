package libcore

import (
	"os"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/sagernet/sing-box/log"
)

const ProtectPath = "protect_path"

func InitCore(process, cachePath, internalAssets, externalAssets string,
	maxLogLines int32, logLevel int32,
	useOfficialAssets bool,
	debugMode bool,
) {
	defer catchPanic("InitCore", func(panicErr error) { log.Error(panicErr) })
	isBgProcess := strings.HasSuffix(process, ":bg")

	workDir := filepath.Join(cachePath, "../no_backup")
	_ = os.MkdirAll(workDir, 0o755)
	_ = os.Chdir(workDir)
	externalAssetsPath = externalAssets
	internalAssetsPath = internalAssets

	// Set up log
	if maxLogLines < 50 {
		maxLogLines = 50
	}
	_ = setupLog(int(maxLogLines), filepath.Join(externalAssets, "stderr.log"), log.Level(logLevel), isBgProcess)

	if isBgProcess {
		if debugMode {
			runtime.SetMutexProfileFraction(1)
			runtime.SetBlockProfileRate(1)
		}
		go func() {
			defer catchPanic("extractAssets", func(panicErr error) { log.Error(panicErr) })

			extractAssets(useOfficialAssets)
			cleanLogCache(cachePath)
		}()
	}
}
