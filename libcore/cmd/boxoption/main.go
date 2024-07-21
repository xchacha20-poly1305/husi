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
		writer = file
	}

	var classes []string
	for _, opt := range optionList {
		classes = append(classes, buildClass(opt))
	}
	_, _ = io.WriteString(writer, strings.Join(classes, "\n"))
}

var optionList = []any{
	//option.Options{},
	//option.LogOptions{},
	//option.NTPOptions{},

	// DNS
	//option.DNSOptions{},
	option.DNSServerOptions{},
	//option.DNSClientOptions{},
	option.DNSRule{},
	option.DefaultDNSRule{},
	option.LogicalDNSRule{},
	option.DNSFakeIPOptions{},

	// Inbound
	option.Inbound{},
	//option.InboundOptions{},
	option.HTTPMixedInboundOptions{},
	option.TunInboundOptions{},
	option.TunPlatformOptions{},
	option.HTTPOutboundOptions{},

	// Outbound
	option.Outbound{},

	// Experimental
	option.ExperimentalOptions{},
	option.CacheFileOptions{},
	option.ClashAPIOptions{},
	option.V2RayAPIOptions{},
	option.V2RayStatsServiceOptions{},
	option.DebugOptions{},

	// Route
	option.RouteOptions{},
	option.Rule{},
	option.DefaultRule{},
	option.LogicalRule{},
	option.RuleSet{},
	option.PlainRuleSet{},
	option.LocalRuleSet{},
	option.RemoteRuleSet{},
	option.HeadlessRule{},

	// Shared
	option.UDPOverTCPOptions{},
	option.OutboundMultiplexOptions{},
	option.BrutalOptions{},
	option.OutboundTLSOptions{},
	option.OutboundUTLSOptions{},
	option.OutboundRealityOptions{},
	option.OutboundECHOptions{},
	option.V2RayTransportOptions{},
	option.V2RayHTTPOptions{},
	option.V2RayWebsocketOptions{},
	option.V2RayQUICOptions{},
	option.V2RayGRPCOptions{},
	option.V2RayHTTPUpgradeOptions{},

	// Proxy
	option.ShadowsocksOutboundOptions{},
	option.ShadowTLSOutboundOptions{},
	option.SocksOutboundOptions{},
	option.HTTPOutboundOptions{},
	option.SSHOutboundOptions{},
	option.TrojanOutboundOptions{},
	option.TUICOutboundOptions{},
	option.VLESSOutboundOptions{},
	option.VMessOutboundOptions{},
	option.WireGuardOutboundOptions{},
	option.WireGuardPeer{},
}
