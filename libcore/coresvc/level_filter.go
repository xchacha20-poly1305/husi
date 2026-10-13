package coresvc

import (
	"github.com/sagernet/sing-box/log"
)

// levelFilteredWriter drops messages above level before they reach writer.
// A sing-box log factory hands every message to its platform writers whatever
// the configured level, because the in-app log viewer filters on its own side;
// a writer that persists logs has to filter itself.
type levelFilteredWriter struct {
	writer log.PlatformWriter
	level  log.Level
}

// FilterPlatformWriter wraps writer so that only messages at or below level
// (that is, at least as severe) reach it.
func FilterPlatformWriter(writer log.PlatformWriter, level log.Level) log.PlatformWriter {
	return &levelFilteredWriter{writer: writer, level: level}
}

var _ log.PlatformWriter = (*levelFilteredWriter)(nil)

func (w *levelFilteredWriter) WriteMessage(level log.Level, message string) {
	if level > w.level {
		return
	}
	w.writer.WriteMessage(level, message)
}
