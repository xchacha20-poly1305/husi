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

var platformLogWrapper *logWriter
var logWriterDisable = false
var truncateOnStart = true

func setupLog(maxSize int64, path string) (err error) {
	if platformLogWrapper != nil {
		return
	}

	var f *os.File
	f, err = os.OpenFile(path, os.O_RDWR|os.O_APPEND|os.O_CREATE, 0644)
	if err == nil {
		fd := int(f.Fd())
		if truncateOnStart {
			syscall.Flock(fd, syscall.LOCK_EX)
			// Check if need truncate
			if size, _ := f.Seek(0, io.SeekEnd); size > maxSize {
				// read oldBytes for maxSize
				f.Seek(-maxSize, io.SeekCurrent)
				oldBytes, err := io.ReadAll(f)
				if err == nil {
					// truncate file
					err = f.Truncate(0)
					// write oldBytes
					if err == nil {
						f.Write(oldBytes)
					}
				}
			}
			syscall.Flock(fd, syscall.LOCK_UN)
		}
		// redirect stderr
		syscall.Dup3(fd, int(os.Stderr.Fd()), 0)
	}

	if err != nil {
		log.Println(E.Cause(err, "open log"))
	}

	//
	platformLogWrapper = &logWriter{}
	platformLogWrapper.writers = []io.Writer{f}
	// setup std log
	log.SetFlags(log.LstdFlags | log.LUTC)
	log.SetOutput(platformLogWrapper)

	// setup box default log
	boxlog.SetStdLogger(boxlog.NewDefaultFactory(context.Background(),
		boxlog.Formatter{BaseTime: time.Now(), DisableColors: true},
		os.Stderr,
		"",
		platformLogWrapper,
		false).Logger())

	return
}

var _ boxlog.PlatformWriter = (*logWriter)(nil)

type logWriter struct {
	writers []io.Writer
}

func (w *logWriter) DisableColors() bool {
	return true
}

func (w *logWriter) WriteMessage(level boxlog.Level, message string) {
	io.WriteString(w, fmt.Sprintf("%s\n", message))
}

var _ io.Writer = (*logWriter)(nil)

func (w *logWriter) Write(p []byte) (n int, err error) {
	if !logWriterDisable {
		for _, w := range w.writers {
			if w == nil {
				continue
			}
			var fd int
			f, isFile := w.(*os.File)
			if isFile {
				fd = int(f.Fd())
				syscall.Flock(fd, syscall.LOCK_EX)
			}
			w.Write(p)
			if isFile {
				syscall.Flock(fd, syscall.LOCK_UN)
			}
		}
	}
	return len(p), nil
}

var _ io.StringWriter = (*logWriter)(nil)

func (w *logWriter) WriteString(s string) (n int, err error) {
	if !logWriterDisable {
		for _, w := range w.writers {
			if w == nil {
				continue
			}
			var fd int
			f, isFile := w.(*os.File)
			if isFile {
				fd = int(f.Fd())
				syscall.Flock(fd, syscall.LOCK_EX)
			}
			io.WriteString(w, s)
			if isFile {
				syscall.Flock(fd, syscall.LOCK_UN)
			}
		}
	}
	return len(s), nil
}

func (w *logWriter) truncate() {
	for _, w := range w.writers {
		if w == nil {
			continue
		}
		if f, ok := w.(*os.File); ok {
			_ = f.Truncate(0)
		}
	}
}

func (w *logWriter) Close() error {
	for _, w := range w.writers {
		if w == nil {
			continue
		}
		if f, ok := w.(*os.File); ok {
			_ = f.Close()
		}
	}
	return nil
}
