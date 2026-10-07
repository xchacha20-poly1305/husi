package libcore

import (
	"bufio"
	"io"
	"os"
	"path/filepath"
	"sync"
	"testing"

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
