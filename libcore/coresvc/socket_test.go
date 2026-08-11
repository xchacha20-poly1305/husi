package coresvc

import (
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestIsWindowsPipePath(t *testing.T) {
	t.Parallel()
	cases := []struct {
		path string
		want bool
	}{
		{`\\.\pipe\Foo`, true},
		{`\\.\PIPE\Foo`, true},
		{`/var/run/husi`, false},
		{"", false},
		{`C:\pipe`, false},
	}
	for _, tc := range cases {
		assert.Equal(t, tc.want, IsWindowsPipePath(tc.path), "IsWindowsPipePath(%q)", tc.path)
	}
}

func TestClientEndpoint(t *testing.T) {
	t.Parallel()
	pipe := `\\.\pipe\ProtectedPrefix\Administrators\husi`
	assert.Equal(t, pipe, ClientEndpoint(pipe))
	dir := "/var/run/husi"
	assert.Equal(t, filepath.Join(dir, Socket), ClientEndpoint(dir))
}
