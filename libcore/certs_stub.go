//go:build !android && !darwin && !linux && !windows

package libcore

import (
	"os"

	E "github.com/sagernet/sing/common/exceptions"
)

// Unknown platforms have no default SSL_CERT_{FILE,DIR} paths.
var certFiles, certDirectories []string

func appendPlatformRootCAs(roots *rootCABundle, withUserTrust bool) error {
	return E.Cause(os.ErrInvalid, "unexpected platform")
}
