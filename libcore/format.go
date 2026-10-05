package libcore

import (
	"bytes"
	"context"
	"reflect"

	"github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/schema"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/service"

	"github.com/xchacha20-poly1305/husi/libcore/v2/distro"
	"github.com/xchacha20-poly1305/husi/libcore/v2/pb/husi/v1"
	"github.com/xchacha20-poly1305/husi/libcore/v2/plugin/protect"
)

func baseContext(platformInterface PlatformInterface) context.Context {
	dnsRegistry := distro.DNSTransportRegistry()
	registerPlatformLocalDNSTransport(dnsRegistry, platformInterface)
	return box.Context(
		context.Background(),
		distro.InboundRegistry(),
		distro.OutboundRegistry(),
		distro.EndpointRegistry(),
		dnsRegistry,
		distro.ServiceRegistry(),
		distro.CertificateProviderRegistry(),
	)
}

// parseConfig parses configContent to option.Options.
func parseConfig(ctx context.Context, configContent string) (option.Options, error) {
	options, err := json.UnmarshalExtendedContext[option.Options](ctx, []byte(configContent))
	if err != nil {
		return option.Options{}, E.Cause(err, "decode config")
	}
	return options, nil
}

var _ json.CommentUnmarshaler = (*commentedDocument)(nil)

// commentDocument wraps map[string]any to keep comments in json
type commentedDocument struct {
	fields   map[string]any
	comments *json.CommentSet
}

func (c *commentedDocument) MarshalJSONContext(ctx context.Context) ([]byte, error) {
	return json.MarshalContext(ctx, c.fields)
}

func (c *commentedDocument) UnmarshalJSONContext(ctx context.Context, content []byte) error {
	return json.UnmarshalContext(ctx, content, &c.fields)
}

func (c *commentedDocument) Comments() *json.CommentSet {
	return c.comments
}

func (c *commentedDocument) SetComments(comments *json.CommentSet) {
	c.comments = comments
}

// formatConfig formats json, keeping the comments of configContent.
func formatConfig(configContent string) (string, error) {
	ctx := baseContext(nil)
	document, err := json.UnmarshalExtendedContext[commentedDocument](ctx, []byte(configContent))
	if err != nil {
		return "", err
	}

	var buffer bytes.Buffer
	encoder := json.NewEncoderContext(ctx, &buffer)
	const indent = "  " // sing-box style
	encoder.SetIndent("", indent)
	err = encoder.Encode(&document)
	if err != nil {
		return "", err
	}

	return buffer.String(), nil
}

// generateSchema generates the JSON Schema of the option type kind names.
func generateSchema(kind husiv1.SchemaKind) (string, error) {
	var rootType reflect.Type
	switch kind {
	case husiv1.SchemaKind_SCHEMA_KIND_CONFIG:
		rootType = reflect.TypeFor[option.Options]()
	case husiv1.SchemaKind_SCHEMA_KIND_OUTBOUND:
		rootType = reflect.TypeFor[option.Outbound]()
	case husiv1.SchemaKind_SCHEMA_KIND_DNS_RULE:
		rootType = reflect.TypeFor[option.DNSRule]()
	default:
		return "", E.New("unknown schema kind: ", kind.String())
	}
	content, err := schema.Generate(baseContext(nil), rootType)
	if err != nil {
		return "", E.Cause(err, "generate schema for ", rootType)
	}
	return string(content), nil
}

// checkConfig checks whether configContent can run as sing-box configuration.
func checkConfig(configContent string) error {
	ctx := baseContext(nil)
	options, err := parseConfig(ctx, configContent)
	if err != nil {
		return E.Cause(err, "parse config")
	}

	if options.Route != nil {
		// AutoDetectInterface will be automatically enabled by platform interface,
		// while platformInterfaceStub not including it. (tun.ErrNetlinkBanned)
		options.Route.AutoDetectInterface = false
	}

	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	service.MustRegister[adapter.PlatformInterface](ctx, platformInterfaceStub{})
	service.MustRegister[protect.Protector](ctx, protect.ProtectorFunc(func(_ int) error {
		return nil
	}))
	instance, err := box.New(box.Options{
		Options: options,
		Context: ctx,
	})
	if err != nil {
		return E.Cause(err, "create box")
	}
	defer instance.Close()
	return nil
}
