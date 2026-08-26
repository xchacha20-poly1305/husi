//go:build !linux

package daemonhost

func dropAmbientCapabilities() error {
	return nil
}

func hasNetworkAdminCapability() bool {
	return true
}
