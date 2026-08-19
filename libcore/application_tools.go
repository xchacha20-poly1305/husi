package libcore

import (
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
)

func WireApplicationTools(opts *coresvc.HostOptions) {
	opts.GetCert = hostGetCert
	opts.STUNTest = runSTUNTest
	opts.SpeedTest = runSpeedTest
}

func hostGetCert(server, serverName string, mode husiv1.GetCertMode, socksProxyURL string) (string, error) {
	modeStr, err := getCertModeString(mode)
	if err != nil {
		return "", err
	}
	return getCert(server, serverName, modeStr, socksProxyURL)
}

func getCertModeString(mode husiv1.GetCertMode) (string, error) {
	switch mode {
	case husiv1.GetCertMode_GET_CERT_MODE_HTTPS:
		return "https", nil
	case husiv1.GetCertMode_GET_CERT_MODE_QUIC:
		return "quic", nil
	default:
		return "", E.New("unknown get cert mode: ", mode.String())
	}
}
