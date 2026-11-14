package itersequence

import (
	"bufio"
	"iter"
)

func FromScanner(scanner *bufio.Scanner) iter.Seq[string] {
	return func(yield func(string) bool) {
		for scanner.Scan() {
			text := scanner.Text()
			if !yield(text) {
				return
			}
		}
	}
}
