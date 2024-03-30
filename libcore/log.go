package libcore

import (
	"fmt"
	"io"
	stdlog "log"
	"os"
	"syscall"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
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

var platformLogWrapper *logWriter

func setupLog(maxSize int64, path string, enableLog, notTruncateOnStart bool) (err error) {
	if platformLogWrapper != nil {
		return
	}

	var file *os.File
	file, err = os.OpenFile(path, os.O_RDWR|os.O_APPEND|os.O_CREATE, 0644)
	if err == nil {
		fd := int(file.Fd())
		if !notTruncateOnStart {
			_ = syscall.Flock(fd, syscall.LOCK_EX)
			// Check if need truncate
			if size, _ := file.Seek(0, io.SeekEnd); size > maxSize {
				// read oldBytes for maxSize
				_, _ = file.Seek(-maxSize, io.SeekCurrent)
				oldBytes, err := io.ReadAll(file)
				if err == nil {
					// truncate file
					err = file.Truncate(0)
					// write oldBytes
					if err == nil {
						_, _ = file.Write(oldBytes)
					}
				}
			}
			_ = syscall.Flock(fd, syscall.LOCK_UN)
		}
		// redirect stderr
		_ = syscall.Dup3(fd, int(os.Stderr.Fd()), 0)
	}

	if err != nil {
		stdlog.Println(E.Cause(err, "open log"))
	}

	platformLogWrapper = &logWriter{
		disable: !enableLog,
		writer:  file,
	}
	// setup std log
	stdlog.SetFlags(stdlog.LstdFlags | stdlog.LUTC)
	stdlog.SetOutput(platformLogWrapper)

	return
}

var _ log.PlatformWriter = (*logWriter)(nil)

type logWriter struct {
	disable bool
	writer  io.Writer
}

func (w *logWriter) DisableColors() bool {
	return false
}

func (w *logWriter) WriteMessage(_ log.Level, message string) {
	_, _ = io.WriteString(w.writer, fmt.Sprintf("%s\n", message))
}

var _ io.Writer = (*logWriter)(nil)

func (w *logWriter) Write(p []byte) (n int, err error) {
	if w.disable || w.writer == nil {
		return len(p), nil
	}

	file, isFile := w.writer.(*os.File)
	if isFile {
		fd := int(file.Fd())
		_ = syscall.Flock(fd, syscall.LOCK_EX)
		defer syscall.Flock(fd, syscall.LOCK_UN)
	}
	return w.writer.Write(p)
}

func (w *logWriter) truncate() {
	if file, isFile := w.writer.(*os.File); isFile {
		_ = file.Truncate(0)
	}
}

func (w *logWriter) Close() error {
	if file, isFile := w.writer.(*os.File); isFile {
		return file.Close()
	}

	return nil
}
