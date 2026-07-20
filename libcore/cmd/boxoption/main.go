package main

import (
	"flag"
	"os"

	"libcore/plugin/pluginoption"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/memory"
	"github.com/sagernet/sing/common/x/collections"
)

var output string

func main() {
	flag.StringVar(&output, "o", "", "Output file.")
	flag.Parse()

	var writer *os.File
	switch output {
	case "", "stdout":
		writer = os.Stdout
	case "stderr":
		writer = os.Stderr
	default:
		file, err := os.OpenFile(output, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, os.ModePerm)
		if err != nil {
			log.Fatal(err)
		}
		defer file.Close()
		writer = file
	}

	all := []collections.MapEntry[string, []any]{
		{extendsBox, boxList},
		{"Rule", ruleList},
		{"DNSRule", dnsRuleList},
		{"RuleSet", ruleSetList},
		{"V2RayTransportOptions", transportList},
		{"Inbound", inboundList},
		{"Outbound", outboundList},
		{"Endpoint", endpointList},
		{"Service", serviceList},
		{"NewDNSServerOptions", newDNSServerList},
	}
	for _, classes := range all {
		for _, class := range classes.Value {
			_, _ = writer.Write(buildClass(class, classes.Key))
			_, _ = writer.WriteString("\n")
		}
	}

	log.Debug("Constant cap: ", mainBuilderSize, ", Final mainBuilder cap: ", mainBuilder.Cap())
	log.Debug("Used memory: ", memory.Total())
}

var boxList = []any{
	option.Options{},
	option.LogOptions{},
	option.NTPOptions{},
	option.CertificateOptions{},

	// DNS
	option.DNSOptions{},
	// option.NewDNSServerOptions{},
	option.DNSClientOptions{},
	// option.DNSRule{},
	option.OptimisticDNSOptions{},

	// Experimental
	option.ExperimentalOptions{},
	option.CacheFileOptions{},
	option.ClashAPIOptions{},
	option.V2RayAPIOptions{},
	option.V2RayStatsServiceOptions{},
	option.DebugOptions{},

	// Route
	option.RouteOptions{},
	// option.Rule{},
	// option.RuleAction{},
	option.RuleSet{},
	option.HeadlessRule{},

	// Shared
	option.UDPOverTCPOptions{},
	option.OutboundMultiplexOptions{},
	option.BrutalOptions{},
	option.OutboundTLSOptions{},
	option.OutboundUTLSOptions{},
	option.OutboundRealityOptions{},
	option.OutboundECHOptions{},
	option.InboundTLSOptions{},
	option.Hysteria2Obfs{},
	option.Hysteria2Realm{},
	option.Hysteria2RealmPortMapping{},
	option.WireGuardPeer{},
	option.OpenConnectTLSOptions{},
	option.OpenConnectTokenOptions{},
	option.OpenConnectCSDOptions{},
	option.OpenConnectHIPOptions{},
	option.OpenConnectTNCCOptions{},
	option.OpenConnectTNCCCertificateOptions{},
	option.OpenConnectFormEntryOptions{},
	option.OpenVPNOutboundTLSOptions{},
	option.OpenVPNControlWrapOptions{},
	option.OpenVPNRemoteOptions{},
	option.OpenVPNPullFilterOptions{},
	// option.V2RayTransportOptions{},
	option.DomainResolveOptions{},
	option.CertificateProvider{},
	option.CertificateProviderOptions{},
	option.HTTPClient{},
	option.HTTPClientOptions{},
	option.NetworkNamespace{},

	// MITM
	// option.MITMOptions{},
	// option.TLSDecryptionOptions{},
	// option.MITMRouteOptions{},
	// option.Script{},
	// option.LocalScriptSource{},
	// option.RemoteScriptSource{},
}

var ruleList = []any{
	option.DefaultRule{},
	option.LogicalRule{},
}

var dnsRuleList = []any{
	option.DefaultDNSRule{},
	option.LogicalDNSRule{},
}

var ruleSetList = []any{
	option.PlainRuleSet{},
	option.LocalRuleSet{},
	option.RemoteRuleSet{},
}

var transportList = []any{
	option.V2RayHTTPOptions{},
	option.V2RayWebsocketOptions{},
	option.V2RayQUICOptions{},
	option.V2RayGRPCOptions{},
	option.V2RayHTTPUpgradeOptions{},
}

var inboundList = []any{
	option.HTTPMixedInboundOptions{},
	option.TunInboundOptions{},
	option.TunPlatformOptions{},
	option.HTTPProxyOptions{},
	option.DirectInboundOptions{},
}

var outboundList = []any{
	option.DirectOutboundOptions{},
	option.ShadowsocksOutboundOptions{},
	option.ShadowTLSOutboundOptions{},
	option.SelectorOutboundOptions{},
	option.URLTestOutboundOptions{},
	option.SOCKSOutboundOptions{},
	// option.HTTPOutboundOptions{},
	pluginoption.HTTPOutboundOptions{},
	option.SSHOutboundOptions{},
	option.TrojanOutboundOptions{},
	option.HysteriaOutboundOptions{},
	option.Hysteria2OutboundOptions{},
	option.TUICOutboundOptions{},
	// option.VLESSOutboundOptions{},
	pluginoption.VLESSOutboundOptions{},
	option.VMessOutboundOptions{},
	option.AnyTLSOutboundOptions{},
	pluginoption.JuicityOutboundOptions{},
	option.NaiveOutboundOptions{},
	pluginoption.TrustTunnelOutboundOptions{},
	option.SnellOutboundOptions{},
	option.BridgeOutboundOptions{},
}

var endpointList = []any{
	option.WireGuardEndpointOptions{},
	option.OpenConnectEndpointOptions{},
	option.OpenVPNClientEndpointOptions{},
}

var serviceList = []any{
	pluginoption.AnchorServiceOptions{},
}

type inlineExtensionSpec struct {
	target     any
	belongs    string
	extensions []any
}

// inlineExtensions lists struct types whose JSON fields are flattened into another class via
// custom Marshal/Unmarshal (e.g., sing-box uses badjson.MarshallObjects to inline
// Hysteria2ObfsGecko fields into Hysteria2Obfs based on the obfs type).
var inlineExtensions = buildInlineExtensions([]inlineExtensionSpec{
	{
		target:     option.Hysteria2Obfs{},
		belongs:    extendsBox,
		extensions: []any{option.Hysteria2ObfsGecko{}},
	},
	{
		target:     option.HeadlessRule{},
		belongs:    extendsBox,
		extensions: []any{option.DefaultHeadlessRule{}, option.LogicalHeadlessRule{}},
	},
	{
		target:     option.HTTPClient{},
		belongs:    extendsBox,
		extensions: []any{option.QUICOptions{}},
	},
	{
		target:     option.HTTPClientOptions{},
		belongs:    extendsBox,
		extensions: []any{option.QUICOptions{}},
	},
	{
		target:     option.SnellOutboundOptions{},
		belongs:    "Outbound",
		extensions: []any{option.SnellObfsClientOptions{}, option.SnellV6Options{}},
	},
	{
		target:     option.NetworkNamespace{},
		belongs:    extendsBox,
		extensions: []any{option.DefaultNetworkNamespaceOptions{}, option.UnshareNetworkNamespaceOptions{}},
	},
})

func buildInlineExtensions(specs []inlineExtensionSpec) map[string][]any {
	extensions := make(map[string][]any, len(specs))
	for _, spec := range specs {
		key := generatedClassNameOf(spec.target, spec.belongs)
		if _, exists := extensions[key]; exists {
			panic("duplicate inline extension target: " + key)
		}
		extensions[key] = spec.extensions
	}
	return extensions
}

var newDNSServerList = []any{
	option.HostsDNSServerOptions{},
	option.LocalDNSServerOptions{},
	option.RemoteDNSServerOptions{},
	option.RemoteTLSDNSServerOptions{},
	option.RemoteHTTPSDNSServerOptions{},
	option.FakeIPDNSServerOptions{},
	option.MDNSDNSServerOptions{},
}
