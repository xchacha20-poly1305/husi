package libcore

import (
	"context"
	"fmt"
	"io"
	"log"
	"os"
	"syscall"
	"time"

	boxlog "github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
)

func LogDebug(l string) {
	boxlog.Debug(l)
}

func LogInfo(l string) {
	boxlog.Info(l)
}

func LogWarning(l string) {
	boxlog.Warn(l)
}

func LogError(l string) {
	boxlog.Error(l)
}

var platformLogWrapper *logWriter

func setupLog(maxSize int64, path string, enableLog, notTruncateOnStart bool) (err error) {
	if platformLogWrapper != nil {
		return
	}

	var f *os.File
	f, err = os.OpenFile(path, os.O_RDWR|os.O_APPEND|os.O_CREATE, 0644)
	if err == nil {
		fd := int(f.Fd())
		if !notTruncateOnStart {
			_ = syscall.Flock(fd, syscall.LOCK_EX)
			// Check if need truncate
			if size, _ := f.Seek(0, io.SeekEnd); size > maxSize {
				// read oldBytes for maxSize
				_, _ = f.Seek(-maxSize, io.SeekCurrent)
				oldBytes, err := io.ReadAll(f)
				if err == nil {
					// truncate file
					err = f.Truncate(0)
					// write oldBytes
					if err == nil {
						_, _ = f.Write(oldBytes)
					}
				}
			}
			_ = syscall.Flock(fd, syscall.LOCK_UN)
		}
		// redirect stderr
		_ = syscall.Dup3(fd, int(os.Stderr.Fd()), 0)
	}

	if err != nil {
		log.Println(E.Cause(err, "open log"))
	}

	//
	platformLogWrapper = &logWriter{
		disable: !enableLog,
		writer:  f,
	}
	// setup std log
	log.SetFlags(log.LstdFlags | log.LUTC)
	log.SetOutput(platformLogWrapper)

	// setup box default log
	boxlog.SetStdLogger(boxlog.NewDefaultFactory(context.Background(),
		boxlog.Formatter{BaseTime: time.Now(), DisableColors: false},
		os.Stderr,
		"",
		platformLogWrapper,
		false).Logger())

	return
}

var _ boxlog.PlatformWriter = (*logWriter)(nil)

type logWriter struct {
	disable bool
	writer  io.Writer
}

func (w *logWriter) DisableColors() bool {
	return false
}

func (w *logWriter) WriteMessage(level boxlog.Level, message string) {
	_, _ = io.WriteString(w.writer, fmt.Sprintf("%s\n", message))
}

var _ io.Writer = (*logWriter)(nil)

func (w *logWriter) Write(p []byte) (n int, err error) {
	if w.disable || w.writer == nil {
		return len(p), nil
	}

	f, isFile := w.writer.(*os.File)
	if isFile {
		fd := int(f.Fd())
		_ = syscall.Flock(fd, syscall.LOCK_EX)
		defer syscall.Flock(fd, syscall.LOCK_UN)
	}
	return w.writer.Write(p)
}

func (w *logWriter) truncate() {
	if f, ok := w.writer.(*os.File); ok {
		_ = f.Truncate(0)
	}
}

func (w *logWriter) Close() error {
	if f, ok := w.writer.(*os.File); ok {
		return f.Close()
	}

	return nil
}
