package libcore

import (
	"encoding/pem"

	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"

	"github.com/xchacha20-poly1305/sing-trusttunnel/tturl"
)

type TrustTunnelURL struct {
	Host             string
	Port             int32
	ServerName       string
	Username         string
	Password         string
	SkipVerification bool
	Certificate      string // pem
	QUIC             bool
	Name             string
}

func (t *TrustTunnelURL) Build() (string, error) {
	var der []byte
	if t.Certificate != "" {
		block, _ := pem.Decode([]byte(t.Certificate))
		if block == nil {
			return "", E.New("invalid certificate")
		}
		der = block.Bytes
	}
	hostname := t.ServerName
	if hostname == "" {
		hostname = t.Host
	}
	var upstreamProtocol tturl.UpstreamProtocol
	if t.QUIC {
		upstreamProtocol = tturl.UpstreamProtocolHTTP3
	} else {
		upstreamProtocol = tturl.UpstreamProtocolHTTP2
	}
	url := &tturl.URL{
		Hostname:         hostname,
		Addresses:        []M.Socksaddr{M.ParseSocksaddrHostPort(t.Host, uint16(t.Port))},
		CustomSNI:        t.ServerName,
		Username:         t.Username,
		Password:         t.Password,
		SkipVerification: t.SkipVerification,
		Certificate:      der,
		UpstreamProtocol: upstreamProtocol,
		Name:             t.Name,
	}
	return url.Build()
}

func ParseTrustTunnelLink(link string) (*TrustTunnelURL, error) {
	url, err := tturl.Parse(link)
	if err != nil {
		return nil, err
	}
	return &TrustTunnelURL{
		Host:             url.Addresses[0].AddrString(),
		Port:             int32(url.Addresses[0].Port),
		ServerName:       url.CustomSNI,
		Username:         url.Username,
		Password:         url.Password,
		SkipVerification: url.SkipVerification,
		Certificate: string(pem.EncodeToMemory(&pem.Block{
			Type:  "CERTIFICATE",
			Bytes: url.Certificate,
		})),
		QUIC: url.UpstreamProtocol == tturl.UpstreamProtocolHTTP3,
		Name: url.Name,
	}, nil
}
