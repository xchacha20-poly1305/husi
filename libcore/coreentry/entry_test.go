//go:build !android

package coreentry

import (
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestSplitServiceArgs(t *testing.T) {
	tests := []struct {
		name     string
		args     []string
		wantVerb string
		wantRest []string
		wantErr  bool
	}{
		{
			name:     "verb first",
			args:     []string{"install", "--dir", "/var/lib/husi"},
			wantVerb: "install",
			wantRest: []string{"--dir", "/var/lib/husi"},
		},
		{
			name:     "flags first",
			args:     []string{"--dir", "/tmp", "--purge", "uninstall"},
			wantVerb: "uninstall",
			wantRest: []string{"--dir", "/tmp", "--purge"},
		},
		{
			name:     "status only",
			args:     []string{"status"},
			wantVerb: "status",
			wantRest: []string{},
		},
		{
			name:    "missing verb",
			args:    []string{"--dir", "/tmp"},
			wantErr: true,
		},
		{
			name:    "double verb",
			args:    []string{"start", "stop"},
			wantErr: true,
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			verb, rest, err := splitServiceArgs(tt.args)
			if tt.wantErr {
				require.Error(t, err)
				return
			}
			require.NoError(t, err)
			assert.Equal(t, tt.wantVerb, verb)
			assert.Equal(t, tt.wantRest, rest)
		})
	}
}

func TestMainVersion(t *testing.T) {
	code := Main([]string{"husi-core", "version"})
	assert.Equal(t, 0, code)
}

func TestMainUnknown(t *testing.T) {
	code := Main([]string{"husi-core", "nope"})
	assert.Equal(t, 2, code)
}

func TestMainMissingCommand(t *testing.T) {
	code := Main([]string{"husi-core"})
	assert.Equal(t, 2, code)
}
