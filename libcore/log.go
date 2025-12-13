package libcore

import (
	"context"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"time"

	"libcore/ringqueue"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	"golang.org/x/sys/unix"
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

func RegisterLogWatcher(watcher LogWatcher) {
	if platformLogWrapper == nil {
		return
	}
	platformLogWrapper.RegisterWatcher(watcher)
}

var (
	platformLogWrapper *logWriter

	logFactory log.Factory
)

func setupLog(capacity int, path string, level log.Level, notTruncateOnStart bool) (err error) {
	if platformLogWrapper != nil {
		return
	}

	var file *os.File
	flags := os.O_CREATE | os.O_WRONLY
	if notTruncateOnStart {
		flags |= os.O_APPEND
	} else {
		flags |= os.O_TRUNC
	}
	file, err = os.OpenFile(path, flags, 0o644)
	if err != nil {
		_, _ = os.Stderr.WriteString(E.Cause(err, "open log").Error())
		return
	}
	fd := int(file.Fd())
	// redirect stderr
	_ = unix.Dup3(fd, int(os.Stderr.Fd()), 0)

	platformLogWrapper = newLogWriter(file, capacity)
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
		false,
	)
	logFactory.SetLevel(level)
	log.SetStdLogger(logFactory.Logger())

	return
}

// cleanLogCache removes old log files from the specified cache directory.
// Before creating this function, the generated log will not be cleaned until user clean it by themselves.
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

type LogItem struct {
	level   log.Level
	Message string
}

func (l *LogItem) GetLevel() int16 {
	return int16(l.level)
}

type LogItemIterator interface {
	Next() *LogItem
	HasNext() bool
	Length() int32
}

type LogWatcher interface {
	AddAll(LogItemIterator)
	Append(*LogItem)
}

var _ log.PlatformWriter = (*logWriter)(nil)

type logWriter struct {
	writer  io.Writer
	buffer  *ringqueue.RingQueue[*LogItem]
	watcher LogWatcher
}

func newLogWriter(writer io.Writer, capacity int) *logWriter {
	return &logWriter{
		writer: writer,
		buffer: ringqueue.New[*LogItem](capacity),
	}
}

func (w *logWriter) DisableColors() bool {
	return false
}

func (w *logWriter) WriteMessage(level log.Level, message string) {
	item := &LogItem{
		level:   level,
		Message: message,
	}
	w.buffer.Add(item)
	if w.watcher != nil {
		w.watcher.Append(item)
	}
	_, _ = io.WriteString(w.writer, message+"\n")
}

var _ io.Writer = (*logWriter)(nil)

func (w *logWriter) Write(p []byte) (n int, err error) {
	if w.writer == nil {
		return len(p), nil
	}

	file, isFile := w.writer.(*os.File)
	if isFile {
		fd := int(file.Fd())
		_ = unix.Flock(fd, unix.LOCK_EX)
		defer unix.Flock(fd, unix.LOCK_UN)
	}
	return w.writer.Write(p)
}

func (w *logWriter) truncate() {
	if file, isFile := w.writer.(*os.File); isFile {
		_ = file.Truncate(0)
	}
}

func (w *logWriter) Close() error {
	w.buffer.Clear()
	return common.Close(w.writer)
}

func (w *logWriter) RegisterWatcher(watcher LogWatcher) {
	if w.watcher != nil {
		w.watcher = nil
		return
	}
	w.watcher = watcher
	watcher.AddAll(newIterator(w.buffer.All()))
}

func (w *logWriter) Clear() {
	w.truncate()
	w.buffer.Clear()
}
