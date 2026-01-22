package libcore

import (
	"context"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"sync"
	"time"

	"libcore/ringqueue"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/binary"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/observable"
	"github.com/sagernet/sing/common/varbin"

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

var (
	platformLogWrapper *logWriter

	logFactory log.ObservableFactory
)

func setupLog(maxLogLine int, path string, level log.Level, notTruncateOnStart bool) (err error) {
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

	platformLogWrapper = newLogWriter(file, maxLogLine)
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
	Level   log.Level
	Message string
}

func (l *LogItem) GetLevel() int32 {
	return int32(l.Level)
}

var (
	_ log.PlatformWriter               = (*logWriter)(nil)
	_ observable.Observable[log.Entry] = (*logWriter)(nil)
)

type logWriter struct {
	writer   io.Writer
	bufferAccess sync.RWMutex
	buffer   *ringqueue.RingQueue[log.Entry]
	observer *observable.Observer[log.Entry]
}

func newLogWriter(writer io.Writer, capacity int) *logWriter {
	subscriber := observable.NewSubscriber[log.Entry](128)
	return &logWriter{
		writer:   writer,
		buffer:   ringqueue.New[log.Entry](capacity),
		observer: observable.NewObserver(subscriber, 64),
	}
}

func (w *logWriter) DisableColors() bool {
	return false
}

func (w *logWriter) WriteMessage(level log.Level, message string) {
	entry := log.Entry{
		Level:   level,
		Message: message,
	}
	w.bufferAccess.Lock()
	w.buffer.Add(entry)
	w.bufferAccess.Unlock()
	w.observer.Emit(entry)
	_, _ = io.WriteString(w.writer, message+"\n")
}

func (w *logWriter) Subscribe() (subscription observable.Subscription[log.Entry], done <-chan struct{}, err error) {
	return w.observer.Subscribe()
}

func (w *logWriter) UnSubscribe(subscription observable.Subscription[log.Entry]) {
	w.observer.UnSubscribe(subscription)
}

func (w *logWriter) All() []log.Entry {
	w.bufferAccess.RLock()
	defer w.bufferAccess.RUnlock()
	return w.buffer.All()
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
	return common.Close(
		w.writer,
		w.observer,
	)
}

func (w *logWriter) Clear() {
	w.truncate()
	w.bufferAccess.Lock()
	defer w.bufferAccess.Unlock()
	w.buffer.Clear()
}

type LogItemFunc interface {
	Invoke(*LogItem)
}

func (c *Client) SubscribeLogs(callback LogItemFunc) error {
	err := varbin.Write(c.conn, binary.BigEndian, commandSubscribeLogs)
	if err != nil {
		return E.Cause(err, "write command")
	}
	for {
		// Struct are same
		item, err := varbin.ReadValue[LogItem](c.conn, binary.BigEndian)
		if err != nil {
			if E.IsClosed(err) {
				return nil
			}
			return E.Cause(err, "read log entry")
		}
		callback.Invoke(&item)
	}
}

func (s *Service) handleSubscribeLogs(conn io.ReadWriter) error {
	buffer := platformLogWrapper.All()
	for i := range buffer {
		err := varbin.Write(conn, binary.BigEndian, buffer[i])
		if err != nil {
			return E.Cause(err, "write log entry buffer ", i)
		}
	}
	subscription, done, err := platformLogWrapper.Subscribe()
	if err != nil {
		return E.Cause(err, "subscribe log factory")
	}
	defer platformLogWrapper.UnSubscribe(subscription)
	for {
		select {
		case entry := <-subscription:
			err := varbin.Write(conn, binary.BigEndian, entry)
			if err != nil {
				return E.Cause(err, "write entry")
			}
		case <-done:
			return nil
		}
	}
}

func (c *Client) ClearLog() error {
	err := varbin.Write(c.conn, binary.BigEndian, commandClearLog)
	if err != nil {
		return E.Cause(err, "write command")
	}
	return nil
}
