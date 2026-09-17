package systemproxy

import (
	"iter"
	"strconv"
	"strings"
)

func parseNetworkServices(output string) iter.Seq[string] {
	return func(yield func(string) bool) {
		isFirst := true
		for line := range strings.Lines(output) {
			if isFirst {
				isFirst = false
				continue
			}
			line = strings.TrimSpace(line)
			if line == "" || strings.HasPrefix(line, "*") {
				continue
			}
			if !yield(line) {
				return
			}
		}
	}
}

func networksetupEnableArgs(service, host string, port uint16) [][]string {
	portStr := strconv.FormatUint(uint64(port), 10)
	return [][]string{
		{"-setwebproxy", service, host, portStr},
		{"-setsecurewebproxy", service, host, portStr},
		{"-setsocksfirewallproxy", service, host, portStr},
	}
}

func networksetupDisableArgs(service string) [][]string {
	return [][]string{
		{"-setwebproxystate", service, "off"},
		{"-setsecurewebproxystate", service, "off"},
		{"-setsocksfirewallproxystate", service, "off"},
	}
}
