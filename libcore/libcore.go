package libcore

import (
	"os"
	"path/filepath"
	"runtime"

	_ "github.com/sagernet/gomobile"
	"github.com/sagernet/sing-box/log"
)

const protectPathName = "protect_path"

var protectSocketPath = protectPathName

// InitCore prepares the :bg process, the only one that loads libcore.
func InitCore(
	cachePath, internalAssets, externalAssets string,
	maxLogLines int32, logLevel int32,
	debugMode bool,
) {
	defer catchPanic("InitCore", func(panicErr error) { log.Error(panicErr) })

	workDir := filepath.Join(cachePath, "../no_backup")
	_ = os.MkdirAll(workDir, 0o755)
	_ = os.Chdir(workDir)
	protectSocketPath = filepath.Join(workDir, protectPathName)
	externalAssetsPath = externalAssets
	internalAssetsPath = internalAssets

	// Set up log
	if maxLogLines < 50 {
		maxLogLines = 50
	}
	_ = setupLog(int(maxLogLines), filepath.Join(externalAssets, "stderr.log"), log.Level(logLevel))

	if debugMode {
		runtime.SetMutexProfileFraction(1)
		runtime.SetBlockProfileRate(1)
	}
	go func() {
		defer catchPanic("cleanFiles", func(panicErr error) { log.Error(panicErr) })

		deleteDeprecated()
		cleanLogCache(cachePath)
	}()
}
