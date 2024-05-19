package libcore

import (
	"strings"
)

// stringBetween returns the string of str between start and end.
func stringBetween(str string, start string, end string) string {
	s := strings.Index(str, start)
	if s < 0 {
		return str
	}

	s += len(start)
	e := strings.Index(str[s:], end)
	if e < 0 {
		return str[s:]
	}

	return str[s : s+e]
}
