//go:build unix

package daemonhost

import "syscall"

func closeProcessCredential(attr *syscall.SysProcAttr) {}
