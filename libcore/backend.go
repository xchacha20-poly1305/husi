package libcore

import (
	"context"

	E "github.com/sagernet/sing/common/exceptions"

	"github.com/xchacha20-poly1305/husi/libcore/v2/coresvc"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
)

type HostBackend struct{}

func (HostBackend) CheckConfig(config string) error {
	return CheckConfig(config)
}

func (HostBackend) GenerateSchema(kind husiv1.SchemaKind) (string, error) {
	switch kind {
	case husiv1.SchemaKind_SCHEMA_KIND_CONFIG:
		return GenerateConfigSchema()
	case husiv1.SchemaKind_SCHEMA_KIND_OUTBOUND:
		return GenerateOutboundSchema()
	case husiv1.SchemaKind_SCHEMA_KIND_DNS_RULE:
		return GenerateDNSRuleSchema()
	default:
		return "", E.New("unknown schema kind: ", kind.String())
	}
}

func (HostBackend) StandaloneURLTest(config, outboundTag, link string, timeoutMs int32, options uint8, plugins []*husiv1.PluginProcessSpec) (int32, error) {
	return StandaloneURLTest(config, outboundTag, link, timeoutMs, options, nil)
}

func (HostBackend) GetCert(ctx context.Context, server, serverName string, mode husiv1.GetCertMode, socksProxyURL string) (string, error) {
	return hostGetCert(ctx, server, serverName, mode, socksProxyURL)
}

func (HostBackend) STUNTest(ctx context.Context, server, socksProxyURL string, sender coresvc.STUNTestSender) error {
	return runSTUNTest(ctx, server, socksProxyURL, sender)
}

func (HostBackend) SpeedTest(ctx context.Context, request *husiv1.SpeedTestRequest, sender coresvc.SpeedTestSender) error {
	return runSpeedTest(ctx, request, sender)
}

func (HostBackend) BuildEnvironment() string {
	return BuildEnvironment()
}

type serviceBackend struct {
	HostBackend
	service *Service
}

func (b *serviceBackend) StandaloneURLTest(config, outboundTag, link string, timeoutMs int32, options uint8, plugins []*husiv1.PluginProcessSpec) (int32, error) {
	return b.service.standaloneURLTest(config, outboundTag, link, timeoutMs, options, plugins)
}

func hostGetCert(ctx context.Context, server, serverName string, mode husiv1.GetCertMode, socksProxyURL string) (string, error) {
	modeStr, err := getCertModeString(mode)
	if err != nil {
		return "", err
	}
	return getCert(ctx, server, serverName, modeStr, socksProxyURL)
}

func getCertModeString(mode husiv1.GetCertMode) (string, error) {
	switch mode {
	case husiv1.GetCertMode_GET_CERT_MODE_HTTPS:
		return "https", nil
	case husiv1.GetCertMode_GET_CERT_MODE_QUIC:
		return "quic", nil
	default:
		return "", E.New("unknown get cert mode: ", mode)
	}
}
