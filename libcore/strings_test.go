package libcore

import (
	"testing"
)

func TestStringBetween(t *testing.T) {
	tests := []struct {
		input      string
		start, end string
		expected   string
	}{
		{
			input:    "gopher",
			start:    "go",
			end:      "er",
			expected: "ph",
		},
		{
			input:    "gogogo",
			start:    "go",
			end:      "go",
			expected: "",
		},
	}

	for _, tt := range tests {
		str := stringBetween(tt.input, tt.start, tt.end)
		if str != tt.expected {
			t.Errorf("Want %s got %s", tt.expected, str)
		}
	}
}
