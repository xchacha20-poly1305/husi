//go:build !linux && !darwin && !windows

package daemonhost

func defaultWorkingDir() string {
	return "/var/lib/husi"
}

func defaultSocketPath() string {
	return "/var/run/husi/api.sock"
}
