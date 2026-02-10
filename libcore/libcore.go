package libcore

import (
	"os"
	"path/filepath"
	"runtime"

	"github.com/sagernet/sing-box/log"

	_ "github.com/xchacha20-poly1305/anja"
)

const ProtectPath = "protect_path"

func InitCore(shouldOperateFiles bool,
	cachePath, internalAssets, externalAssets string,
	maxLogLines int32, logLevel int32,
	useOfficialAssets bool,
	debugMode bool,
) {
	defer catchPanic("InitCore", func(panicErr error) { log.Error(panicErr) })

	workDir := filepath.Join(cachePath, "../no_backup")
	_ = os.MkdirAll(workDir, 0o755)
	_ = os.Chdir(workDir)
	externalAssetsPath = externalAssets
	internalAssetsPath = internalAssets

	// Set up log
	if maxLogLines < 50 {
		maxLogLines = 50
	}
	_ = setupLog(int(maxLogLines), filepath.Join(externalAssets, "stderr.log"), log.Level(logLevel), shouldOperateFiles)

	if shouldOperateFiles {
		if debugMode {
			runtime.SetMutexProfileFraction(1)
			runtime.SetBlockProfileRate(1)
		}
		go func() {
			defer catchPanic("extractAssets", func(panicErr error) { log.Error(panicErr) })

			deleteDeprecated()
			cleanLogCache(cachePath)
			if useOfficialAssets {
				ExtractAssets()
			}
		}()
	}
}
