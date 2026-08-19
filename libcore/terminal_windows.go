package libcore

import (
	"golang.org/x/sys/windows"
)

func isTerminal(fd int) bool {
	var mode uint32
	err := windows.GetConsoleMode(windows.Handle(fd), &mode)
	return err == nil
}
