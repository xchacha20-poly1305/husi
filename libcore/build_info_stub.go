//go:build !android

package libcore

import (
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

func readAndroidVPNTypes([]string) (*husiv1.AndroidVPNType, error) {
	return nil, status.Error(codes.Unimplemented, "Android VPN apps exist only on Android")
}
