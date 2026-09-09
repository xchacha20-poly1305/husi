//go:build linux

package daemonhost

import (
	_ "embed"
	"os"
	"path/filepath"

	E "github.com/sagernet/sing/common/exceptions"
)

const (
	polkitActionTakeOver  = "fr.husi.take-over-service"
	polkitActionDirectory = "/usr/share/polkit-1/actions"
	polkitActionFileName  = "husi-daemon.policy"
	polkitActionFileMode  = 0o644
)

//go:embed husi-daemon.policy
var polkitActionDocument []byte

func polkitActionPath() string {
	return filepath.Join(polkitActionDirectory, polkitActionFileName)
}

func installPolkitAction() error {
	_, err := os.Stat(polkitActionDirectory)
	if err != nil {
		if os.IsNotExist(err) {
			return E.New("polkit is not installed, take over will be refused")
		}
		return E.Cause(err, "inspect polkit action directory")
	}
	err = atomicWriteFile(polkitActionPath(), polkitActionDocument, polkitActionFileMode)
	if err != nil {
		return E.Cause(err, "write polkit action")
	}
	return nil
}

func removePolkitAction() error {
	err := os.Remove(polkitActionPath())
	if err != nil && !os.IsNotExist(err) {
		return E.Cause(err, "remove polkit action")
	}
	return nil
}
