package externalapi

import (
	"github.com/sagernet/sing-box/option"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json/badoption"
)

type Options struct {
	option.ListenOptions
	Secret                           string                     `json:"secret,omitempty"`
	AccessControlAllowOrigin         badoption.Listable[string] `json:"access_control_allow_origin,omitempty"`
	AccessControlAllowPrivateNetwork bool                       `json:"access_control_allow_private_network,omitempty"`
	option.InboundTLSOptionsContainer
}

func (o Options) Validate() error {
	if o.BindInterface != "" {
		return E.New("bind_interface is not supported on the daemon API endpoint")
	}
	if o.TLS == nil {
		return nil
	}
	//nolint:staticcheck
	if o.TLS.ACME != nil {
		return E.New("tls.acme is not supported on the daemon API endpoint")
	}
	if o.TLS.CertificateProvider != nil {
		return E.New("tls.certificate_provider is not supported on the daemon API endpoint")
	}
	return nil
}
