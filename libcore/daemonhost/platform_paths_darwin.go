//go:build darwin

package daemonhost

func defaultWorkingDir() string {
	return "/Library/Application Support/husi"
}

func defaultSocketPath() string {
	return DefaultDaemonSocketPath
}
