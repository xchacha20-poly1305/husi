package main

import (
	"flag"
	"io"
	"log"
	"os"
	"strings"

	"github.com/sagernet/sing-box/option"
)

//go:generate go run .

var output string

func main() {
	flag.StringVar(&output, "o", "", "Output file.")
	flag.Parse()

	var writer io.Writer
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

	allList := []withExtends{
		{boxList, extendsBox},
		{ruleList, "Rule"},
		{dnsRuleList, "DNSRule"},
		{ruleSetList, "RuleSet"},
		{transportList, "V2RayTransportOptions"},
		{inboundList, "Inbound"},
		{outboundList, "Outbound"},
	}
	var finalLength int
	for _, list := range allList {
		finalLength += len(list.list)
	}
	classes := make([]string, 0, finalLength)
	for _, list := range allList {
		for _, opt := range list.list {
			classes = append(classes, buildClass(opt, list.extends))
		}
	}
	_, _ = io.WriteString(writer, strings.Join(classes, "\n"))
}

type withExtends struct {
	list    []any
	extends string
}

var boxList = []any{
	option.Options{},
	option.LogOptions{},
	option.NTPOptions{},

	// DNS
	option.DNSOptions{},
	option.DNSServerOptions{},
	option.DNSClientOptions{},
	// option.DNSRule{},
	option.DNSFakeIPOptions{},

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
	// option.V2RayTransportOptions{},
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
	option.InboundOptions{},
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
	option.SocksOutboundOptions{},
	option.HTTPOutboundOptions{},
	option.SSHOutboundOptions{},
	option.TrojanOutboundOptions{},
	option.HysteriaOutboundOptions{},
	option.Hysteria2OutboundOptions{},
	option.TUICOutboundOptions{},
	option.VLESSOutboundOptions{},
	option.VMessOutboundOptions{},
	option.WireGuardOutboundOptions{},
	option.WireGuardPeer{},
}
