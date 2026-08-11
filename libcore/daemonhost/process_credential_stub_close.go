//go:build !unix && !windows

package daemonhost

import "syscall"

func closeProcessCredential(attr *syscall.SysProcAttr) {}
