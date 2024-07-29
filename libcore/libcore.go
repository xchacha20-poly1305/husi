package libcore

import (
	"log"
	"os"
	"path/filepath"
	"strings"
	_ "unsafe"
)

const ProtectPath = "protect_path"

func LogPrintln(s string) {
	log.Println(s)
}

func LogClear() {
	platformLogWrapper.truncate()
}

func Kill() {
	os.Exit(0)
}

// SocksInfo saves information for socks.
type SocksInfo struct {
	Port               string
	Username, Password string
}

func NewSocksInfo(port, username, password string) *SocksInfo {
	return &SocksInfo{
		Port:     port,
		Username: username,
		Password: password,
	}
}

func InitCore(process, cachePath, internalAssets, externalAssets string,
	maxLogSizeKb int32, logEnable bool,
	if1 GUIInterface, if2 BoxPlatformInterface,
) {
	defer catchPanic("InitCore", func(panicErr error) { log.Println(panicErr) })
	isBgProcess := strings.HasSuffix(process, ":bg")

	intfGUI = if1
	intfBox = if2
	useProcfs = intfBox.UseProcFS()

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
			extractAssets()
		}
	}()
}
