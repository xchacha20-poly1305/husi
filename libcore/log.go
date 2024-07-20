package libcore

import (
	"io"
	stdlog "log"
	"os"

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

func LogPrintln(s string) {
	stdlog.Println(s)
}

func LogClear() {
	platformLogWrapper.truncate()
}

var platformLogWrapper *logWriter

func setupLog(maxSize int64, path string, enableLog, notTruncateOnStart bool) (err error) {
	if platformLogWrapper != nil {
		return
	}

	var file *os.File
	file, err = os.OpenFile(path, os.O_RDWR|os.O_APPEND|os.O_CREATE, 0o644)
	if err == nil {
		fd := int(file.Fd())
		if !notTruncateOnStart {
			_ = unix.Flock(fd, unix.LOCK_EX)
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
			_ = unix.Flock(fd, unix.LOCK_UN)
		}
		// redirect stderr
		_ = unix.Dup3(fd, int(os.Stderr.Fd()), 0)
	}

	if err != nil {
		stdlog.Println(E.Cause(err, "open log"))
	}

	platformLogWrapper = &logWriter{
		disable: !enableLog,
		writer:  file,
		path:    path,
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
	path    string
}

func (w *logWriter) DisableColors() bool {
	return false
}

func (w *logWriter) WriteMessage(_ log.Level, message string) {
	_, _ = io.WriteString(w.writer, "\n"+message)
}

var _ io.Writer = (*logWriter)(nil)

func (w *logWriter) Write(p []byte) (n int, err error) {
	if w.disable || w.writer == nil {
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
	return common.Close(w.writer)
}
