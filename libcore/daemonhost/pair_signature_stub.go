//go:build !windows

package daemonhost

func VerifyCorePairSignature(shimPath string) error {
	return nil
}
