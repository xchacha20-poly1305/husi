package libcore

import (
	"bytes"
	"cmp"
	"context"
	"crypto/x509"
	"encoding/pem"
	"os"
	"path/filepath"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

	scribe "github.com/xchacha20-poly1305/TLS-scribe"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/rootcerts"
	"github.com/xchacha20-poly1305/husi/libcore/v2/simpleproxyurl"
)

const (
	CertSystem        = int32(rootcerts.StoreSystem)
	CertWithUserTrust = int32(rootcerts.StoreSystemWithUserTrust)
	CertMozilla       = int32(rootcerts.StoreMozilla)
	CertChrome        = int32(rootcerts.StoreChrome)
)

const (
	customCaFile = "ca.pem"
	PluginCaFile = "plugin-ca.pem"
)

// SetupRootCA updates Go trusted certs and creates the PEM bundle for external plugins.
//
// On Android, this appends externalAssetsPath/ca.pem to root CA.
func SetupRootCA(certOption int32) {
	roots := rootcerts.Load(rootcerts.Store(certOption))
	if C.IsAndroid {
		externalPem, _ := os.ReadFile(filepath.Join(externalAssetsPath, customCaFile))
		if len(externalPem) > 0 {
			err := roots.Append(externalPem)
			if err != nil {
				log.Error(E.Cause(err, "load external cert"))
			} else {
				log.Info("loaded external cert")
			}
		}
	}
	rootcerts.Install(roots)

	err := os.MkdirAll(externalAssetsPath, 0o700)
	if err != nil {
		log.Error("create plugin certificate directory: ", err)
		return
	}
	err = os.WriteFile(filepath.Join(externalAssetsPath, PluginCaFile), roots.PEM(), 0o600)
	if err != nil {
		log.Error("write plugin root certificates: ", err)
		return
	}
}

func getCert(ctx context.Context, address, serverName string, mode husiv1.GetCertMode, proxy string) (string, error) {
	target := M.ParseSocksaddr(address)
	target.Port = cmp.Or(target.Port, 443)
	if !target.IsValid() {
		return "", E.New("invalid server address: ", address)
	}
	var dialer N.Dialer = N.SystemDialer
	if proxy != "" {
		var err error
		dialer, err = simpleproxyurl.ProxyFromURL(ctx, proxy)
		if err != nil {
			return "", E.Cause(err, "create proxy dialer")
		}
	}

	options := scribe.Options{
		Target: target,
		SNI:    serverName,
		Dialer: dialer,
	}

	ctx, cancel := context.WithTimeout(ctx, C.ProtocolTimeouts[C.ProtocolQUIC])
	defer cancel()

	var (
		certs []*x509.Certificate
		err   error
	)
	switch mode {
	case husiv1.GetCertMode_GET_CERT_MODE_HTTPS:
		certs, err = scribe.GetCert(ctx, options)
	case husiv1.GetCertMode_GET_CERT_MODE_QUIC:
		certs, err = scribe.GetCertQuic(ctx, options)
	default:
		err = E.New("unknown get cert mode: ", mode.String())
	}
	if err != nil {
		return "", err
	}

	buffer := bytes.NewBuffer(nil)
	for _, cert := range certs {
		_ = pem.Encode(buffer, &pem.Block{
			Type:  "CERTIFICATE",
			Bytes: cert.Raw,
		})
	}
	return buffer.String(), nil
}
