package libcore

import (
	"testing"

	"github.com/stretchr/testify/assert"
)

func Test_IsPreRelease(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected bool
	}{
		{"PreRelease alpha", "1.0.0-alpha", true},
		{"Stable version", "1.0.0", false},
		{"Complex pre-release", "2.1.0-beta.1+build.123", true},
		{"Empty version", "", false},
		{"PreRelease rc", "3.0.0-rc.2", true},
		{"Only build metadata", "1.2.3+build.456", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, IsPreRelease(tt.input))
		})
	}
}
