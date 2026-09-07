package libcore

import (
	"bufio"
	"context"
	"io"
	"os"
	"path/filepath"
	"sync"
	"testing"
	"testing/synctest"
	"time"

	F "github.com/sagernet/sing/common/format"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestLogWriterConcurrentWritesStayWholeLines(t *testing.T) {
	const (
		writerCount    = 8
		linesPerWriter = 250
	)

	path := filepath.Join(t.TempDir(), "stderr.log")
	file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
	require.NoError(t, err)
	defer file.Close()

	writer := newLogWriter([]io.Writer{file, io.Discard})

	var group sync.WaitGroup
	for id := range writerCount {
		group.Go(func() {
			for line := range linesPerWriter {
				writer.WriteMessage(0, F.ToString("writer ", id, " line ", id, line))
			}
		})
	}
	group.Wait()

	written, err := os.Open(path)
	require.NoError(t, err)
	defer written.Close()

	seen := make(map[string]bool, writerCount*linesPerWriter)
	scanner := bufio.NewScanner(written)
	for scanner.Scan() {
		seen[scanner.Text()] = true
	}
	err = scanner.Err()
	require.NoError(t, err)
	for id := range writerCount {
		for line := range linesPerWriter {
			expected := F.ToString("writer ", id, " line ", id, line)
			assert.True(t, seen[expected], "missing or torn line: %q", expected)
		}
	}
	assert.Equal(t, len(seen), writerCount*linesPerWriter)
}

func TestLogWriterTruncateDoesNotBlockWriters(t *testing.T) {
	synctest.Test(t, func(t *testing.T) {
		const (
			rounds  = 200
			timeout = time.Second
		)

		path := filepath.Join(t.TempDir(), "stderr.log")
		file, err := os.OpenFile(path, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0o644)
		require.NoError(t, err)
		defer file.Close()

		writer := newLogWriter([]io.Writer{file})

		ctx, cancel := context.WithTimeout(t.Context(), timeout)
		defer cancel()
		done := make(chan struct{})
		go func() {
			defer close(done)
			var group sync.WaitGroup
			group.Go(func() {
				for round := range rounds {
					writer.WriteMessage(0, F.ToString("line ", round))
				}
			})
			group.Go(func() {
				for range rounds {
					writer.Clear()
				}
			})
			group.Wait()
		}()

		select {
		case <-done:
		case <-ctx.Done():
			t.Fatal("write and truncate deadlocked")
		}
	})
}
