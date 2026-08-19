package libcore

import (
	"context"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"sync"
	"time"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/oscall"
)

func LogDebug(l string) {
	log.Debug(l)
}

func LogInfo(l string) {
	log.Info(l)
}

func LogWarning(l string) {
	log.Warn(l)
}

func LogError(l string) {
	log.Error(l)
}

func LogClear() {
	if platformLogWrapper == nil {
		return
	}
	platformLogWrapper.Clear()
}

func SetLogLevel(level string) {
	if logFactory == nil {
		return
	}
	logLevel, err := log.ParseLevel(level)
	if err != nil {
		log.Error(E.Cause(err, "parse log level"))
		return
	}
	logFactory.SetLevel(logLevel)
}

var (
	platformLogWrapper *logWriter
	logFactory         log.ObservableFactory
	logMaxLines        int
)

func currentLogMaxLines() int {
	if logMaxLines < 50 {
		return 50
	}
	return logMaxLines
}

// fileLogSink returns the file-only platform writer for AttachPlatformWriter.
// Returns a nil interface (not a typed nil) when the sink is unset so callers
// can check `== nil` without the classic Go nil-interface trap.
func fileLogSink() log.PlatformWriter {
	if platformLogWrapper == nil {
		return nil
	}
	return platformLogWrapper
}

func setupLog(maxLogLine int, path string, level log.Level, truncate bool) (err error) {
	if platformLogWrapper != nil {
		return
	}
	if maxLogLine < 50 {
		maxLogLine = 50
	}
	logMaxLines = maxLogLine

	var file *os.File
	flags := os.O_CREATE | os.O_WRONLY
	if truncate {
		flags |= os.O_TRUNC
	} else {
		flags |= os.O_APPEND
	}
	file, err = os.OpenFile(path, flags, 0o644)
	if err != nil {
		_, _ = os.Stderr.WriteString(E.Cause(err, "open log").Error())
		return
	}

	writers := []io.Writer{file}
	if C.IsAndroid {
		fd := int(file.Fd())
		// redirect stderr
		_ = oscall.Dup3(fd, int(os.Stderr.Fd()), 0)
	} else {
		writers = append(writers, os.Stderr)
	}

	platformLogWrapper = newLogWriter(writers)
	logFactory = log.NewDefaultFactory(
		context.Background(),
		log.Formatter{
			BaseTime:         time.Now(),
			DisableTimestamp: true,
			DisableLineBreak: true,
		},
		io.Discard,
		"",
		platformLogWrapper,
		true,
	)
	logFactory.SetLevel(level)
	log.SetStdLogger(logFactory.Logger())

	return
}

// cleanLogCache removes old log files from the specified cache directory.
func cleanLogCache(cacheDir string) {
	logDir := filepath.Join(cacheDir, "log")
	now := time.Now()
	err := filepath.WalkDir(logDir, func(path string, entry fs.DirEntry, err error) (_ error) {
		if err != nil {
			return
		}
		if entry.IsDir() {
			return
		}
		info, err := entry.Info()
		if err != nil {
			log.Warn("cleaning log cache for ", path, ": ", err)
			return
		}
		modificationTime := info.ModTime()
		const cleanTime = 3 * 24 * time.Hour // 3 days
		if modificationTime.IsZero() || now.Sub(modificationTime) >= cleanTime {
			_ = os.Remove(path)
		}
		return
	})
	if err != nil {
		log.Warn("walk log cache: ", err)
	}
}

// logWriter is the file-only sink (flock'd multi-writer). The ring and observer
// live on daemon.StartedService now (D-P1.5).
type logWriter struct {
	writers []io.Writer
	access  sync.Mutex
}

func newLogWriter(writers []io.Writer) *logWriter {
	return &logWriter{writers: writers}
}

func (w *logWriter) DisableColors() bool {
	return false
}

func (w *logWriter) WriteMessage(level log.Level, message string) {
	_, _ = io.WriteString(w, message+"\n")
}

var (
	_ log.PlatformWriter = (*logWriter)(nil)
	_ io.Writer          = (*logWriter)(nil)
)

func (w *logWriter) Write(p []byte) (n int, err error) {
	for _, writer := range w.writers {
		var unlock func() error
		if file, isFile := writer.(*os.File); isFile {
			_ = oscall.Flock(file)
			unlock = func() error { return oscall.FUnlock(file) }
		}
		_, _ = writer.Write(p)
		if unlock != nil {
			_ = unlock()
		}
	}
	return len(p), nil
}

func (w *logWriter) truncate() {
	for _, writer := range w.writers {
		if file, isFile := writer.(*os.File); isFile {
			_ = oscall.Flock(file)
			_ = file.Truncate(0)
			_ = oscall.FUnlock(file)
		}
	}
}

func (w *logWriter) Close() error {
	var errs []error
	for _, writer := range w.writers {
		err := common.Close(writer)
		if err != nil {
			errs = append(errs, err)
		}
	}
	return E.Errors(errs...)
}

func (w *logWriter) Clear() {
	w.truncate()
}
