package main

import (
	"flag"
	"os"

	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/memory"
	"github.com/sagernet/sing/common/x/collections"

	"libcore/plugin/pluginoption"
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
	option.WireGuardPeer{},
	// option.V2RayTransportOptions{},
	option.DomainResolveOptions{},

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
	option.VLESSOutboundOptions{},
	option.VMessOutboundOptions{},
	option.AnyTLSOutboundOptions{},
	pluginoption.JuicityOutboundOptions{},
}

var endpointList = []any{
	option.WireGuardEndpointOptions{},
}

var newDNSServerList = []any{
	option.HostsDNSServerOptions{},
	option.LocalDNSServerOptions{},
	option.RemoteDNSServerOptions{},
	pluginoption.RemoteTCPDNSServerOptions{},
	option.RemoteTLSDNSServerOptions{},
	option.RemoteHTTPSDNSServerOptions{},
	option.FakeIPDNSServerOptions{},
}
