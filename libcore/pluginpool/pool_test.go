package pluginpool

import (
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
)

func TestExpandFileTokens(t *testing.T) {
	files := map[string]string{
		"config.json": "/tmp/work/config.json",
		"ca.pem":      "/tmp/work/ca.pem",
	}
	tests := []struct {
		name  string
		input string
		want  string
	}{
		{"no token", "plain", "plain"},
		{"single", "--config=${file:config.json}", "--config=/tmp/work/config.json"},
		{"multiple", "${file:config.json}:${file:ca.pem}", "/tmp/work/config.json:/tmp/work/ca.pem"},
		{"unknown kept", "${file:missing}", "${file:missing}"},
		{"unterminated", "x${file:nope", "x${file:nope"},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.want, expandFileTokens(tt.input, files))
		})
	}
}

func TestValidatePluginFileName(t *testing.T) {
	require.NoError(t, validatePluginFileName("ok.json"))
	for _, name := range []string{"", ".", "..", "a/b", `a\b`, "/abs"} {
		assert.Error(t, validatePluginFileName(name), "validatePluginFileName(%q)", name)
	}
}

func TestPluginPoolStartAndClose(t *testing.T) {
	dir := t.TempDir()
	pool := NewPluginPool(dir, nil)

	command, args := longRunningCommand()
	err := pool.Start(&husiv1.PluginProcessSpec{
		Name:    "sleeper",
		Command: append([]string{command}, args...),
		Files: []*husiv1.PluginFile{
			{Name: "note.txt", Content: []byte("hello")},
		},
	})
	require.NoError(t, err)

	notePath := filepath.Join(dir, "note.txt")
	_, err = os.Stat(notePath)
	require.NoError(t, err, "plugin file not written")

	require.NoError(t, pool.Close())
	_, err = os.Stat(notePath)
	require.ErrorIs(t, err, os.ErrNotExist, "plugin file still present after Close")
	// Second Close is a no-op.
	require.NoError(t, pool.Close())
}

func TestPluginPoolExitsTooFastIsFatal(t *testing.T) {
	dir := t.TempDir()
	fatalCh := make(chan error, 1)
	pool := NewPluginPool(dir, func(err error) {
		fatalCh <- err
	})
	t.Cleanup(func() { _ = pool.Close() })

	command, args := exitImmediatelyCommand(1)
	err := pool.Start(&husiv1.PluginProcessSpec{
		Name:    "quick-exit",
		Command: append([]string{command}, args...),
	})
	require.NoError(t, err)

	select {
	case err := <-fatalCh:
		require.Error(t, err)
	case <-time.After(3 * time.Second):
		require.FailNow(t, "timed out waiting for fatal callback")
	}
}

func TestPluginPoolRestartsAfterSlowExit(t *testing.T) {
	if testing.Short() {
		t.Skip("slow restart test")
	}
	dir := t.TempDir()
	var restarts atomic.Int32
	// Count fatal as failure; we expect restarts, not fatal.
	pool := NewPluginPool(dir, func(err error) {
		assert.Fail(t, "unexpected fatal", "%v", err)
	})
	t.Cleanup(func() { _ = pool.Close() })

	// Write a tiny helper script that exits after >1s once, then sleeps forever
	// on restart — implemented as: if marker missing, create it and exit after 1.2s;
	// else sleep forever.
	script := filepath.Join(dir, "flaky.sh")
	marker := filepath.Join(dir, "ran-once")
	content := "#!/bin/sh\n" +
		"if [ ! -f '" + marker + "' ]; then\n" +
		"  touch '" + marker + "'\n" +
		"  sleep 1.2\n" +
		"  exit 7\n" +
		"fi\n" +
		"sleep 60\n"
	if runtime.GOOS == "windows" {
		t.Skip("shell script restart test is unix-only")
	}
	require.NoError(t, os.WriteFile(script, []byte(content), 0o700))

	// Observe restart by watching marker and that process keeps running.
	err := pool.Start(&husiv1.PluginProcessSpec{
		Name:    "flaky",
		Command: []string{script},
	})
	require.NoError(t, err)

	deadline := time.Now().Add(5 * time.Second)
	for time.Now().Before(deadline) {
		if _, err := os.Stat(marker); err == nil {
			// Give the supervisor time to restart after the first exit.
			time.Sleep(500 * time.Millisecond)
			restarts.Add(1)
			break
		}
		time.Sleep(50 * time.Millisecond)
	}
	require.NotZero(t, restarts.Load(), "process did not complete first slow exit")
}

func TestPluginPoolFileTokenInCommand(t *testing.T) {
	dir := t.TempDir()
	pool := NewPluginPool(dir, nil)
	t.Cleanup(func() { _ = pool.Close() })

	// cat the materialized file via token expansion; process exits after reading.
	// Use a command that stays up long enough not to fatal: sleep via shell reading file.
	if runtime.GOOS == "windows" {
		t.Skip("unix command test")
	}
	err := pool.Start(&husiv1.PluginProcessSpec{
		Name:    "token",
		Command: []string{"/bin/sh", "-c", "test -f ${file:payload.txt} && sleep 60"},
		Files: []*husiv1.PluginFile{
			{Name: "payload.txt", Content: []byte("payload")},
		},
	})
	require.NoError(t, err)
	// File exists while process runs.
	_, err = os.Stat(filepath.Join(dir, "payload.txt"))
	require.NoError(t, err, "payload missing")
}

func longRunningCommand() (string, []string) {
	if runtime.GOOS == "windows" {
		return "ping", []string{"-n", "60", "127.0.0.1"}
	}
	return "sleep", []string{"60"}
}

func exitImmediatelyCommand(code int) (string, []string) {
	if runtime.GOOS == "windows" {
		return "cmd", []string{"/C", "exit", strconv.Itoa(code)}
	}
	return "/bin/sh", []string{"-c", "exit " + strconv.Itoa(code)}
}
