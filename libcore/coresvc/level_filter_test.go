package coresvc

import (
	"testing"

	"github.com/sagernet/sing-box/log"
)

type recordingPlatformWriter struct {
	levels []log.Level
}

func (w *recordingPlatformWriter) WriteMessage(level log.Level, _ string) {
	w.levels = append(w.levels, level)
}

func TestFilterPlatformWriterDropsLessSevereMessages(t *testing.T) {
	recorder := &recordingPlatformWriter{}
	writer := FilterPlatformWriter(recorder, log.LevelWarn)
	for level := log.LevelPanic; level <= log.LevelTrace; level++ {
		writer.WriteMessage(level, "message")
	}
	expected := []log.Level{log.LevelPanic, log.LevelFatal, log.LevelError, log.LevelWarn}
	if len(recorder.levels) != len(expected) {
		t.Fatalf("got levels %v, want %v", recorder.levels, expected)
	}
	for i, level := range expected {
		if recorder.levels[i] != level {
			t.Fatalf("got levels %v, want %v", recorder.levels, expected)
		}
	}
}
