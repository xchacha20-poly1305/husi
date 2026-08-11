//go:build linux

package daemonhost

func defaultWorkingDir() string {
	return "/var/lib/husi"
}

func defaultSocketPath() string {
	return DefaultDaemonSocketPath
}
