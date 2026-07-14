//go:build !android && !darwin && !linux && !windows

package libcore

import (
	"os"

	E "github.com/sagernet/sing/common/exceptions"
)

func appendSystemRootCAs(roots *rootCABundle, withUserTrust bool) error {
	return E.Cause(os.ErrInvalid, "unexpected platform")
}
