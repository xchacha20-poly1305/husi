package libcore

import (
	"github.com/anytls/sing-anytls/util"
)

// SetAnyTLSVersion aims to combating proxy providers' discrimination
func SetAnyTLSVersion(version string) {
	util.Verison = version
}
