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

func Test_CompareSemver(t *testing.T) {
	tests := []struct {
		name     string
		left     string
		right    string
		expected bool
	}{
		{"Newer patch", "1.0.1", "1.0.0", true},
		{"Older patch", "1.0.0", "1.0.1", false},
		{"Equal", "1.0.0", "1.0.0", false},
		{"Newer minor", "1.1.0", "1.0.9", true},
		{"Newer major", "2.0.0", "1.9.9", true},
		{"Leading v on left", "v1.0.1", "1.0.0", true},
		{"Leading v on both", "v2.1.2", "v2.1.1", true},
		{"Surrounding spaces", " 1.0.1 ", "1.0.0", true},
		{"Release beats pre-release", "1.0.0", "1.0.0-rc.2", true},
		{"Pre-release loses to release", "1.0.0-rc.2", "1.0.0", false},
		{"Newer pre-release", "1.0.0-rc.2", "1.0.0-rc.1", true},
		{"Invalid left", "husi", "1.0.0", false},
		{"Invalid right", "1.0.0", "husi", false},
		{"Empty left", "", "1.0.0", false},
		{"Empty right", "1.0.0", "", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			assert.Equal(t, tt.expected, CompareSemver(tt.left, tt.right))
		})
	}
}
