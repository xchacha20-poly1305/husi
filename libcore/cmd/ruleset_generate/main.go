package main

import (
	"flag"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"

	"github.com/sagernet/sing-box/common/geosite"
	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
)

var (
	geositeDate = flag.String("geosite", "", "geosite date")
	geoipDate   = flag.String("geoip", "", "geoip date")

	output = flag.String("o", "ruleset", "rule set output")
)

const (
	geositeRepo = "v2fly/domain-list-community"
	geoipRepo   = "Dreamacro/maxmind-geoip"

	site     = "geosite"
	siteName = "dlc.dat"
	ip       = "geoip"
	ipName   = "Country.mmdb"
)

var (
	geositeDir string
	geoipDir   string
)

func init() {
	flag.Parse()
}

func main() {
	initDir(*output)

	if *geositeDate != "" {
		geositeData, err := fetch(geositeRepo, *geositeDate, siteName)
		if err != nil {
			log.Fatalln(err)
			return
		}
		domainMap, err := generateGeosite(geositeData)
		if err != nil {
			log.Fatalln(err)
			return
		}
		for code, domains := range domainMap {
			var headlessRule option.DefaultHeadlessRule
			defaultRule := geosite.Compile(domains)
			headlessRule.Domain = defaultRule.Domain
			headlessRule.DomainSuffix = defaultRule.DomainSuffix
			headlessRule.DomainKeyword = defaultRule.DomainKeyword
			headlessRule.DomainRegex = defaultRule.DomainRegex
			var plainRuleSet option.PlainRuleSet
			plainRuleSet.Rules = []option.HeadlessRule{
				{
					Type:           C.RuleTypeDefault,
					DefaultOptions: headlessRule,
				},
			}
			srsPath, _ := filepath.Abs(filepath.Join(geositeDir, "geosite-"+code+".srs"))
			// os.Stderr.WriteString("write " + srsPath + "\n")
			outputRuleSet, err := os.Create(srsPath)
			if err != nil {
				log.Fatalln(err)
				return
			}
			err = srs.Write(outputRuleSet, plainRuleSet, true)
			if err != nil {
				_ = outputRuleSet.Close()
				log.Fatalln(err)
				return
			}
			_ = outputRuleSet.Close()
		}
	}

	if *geoipDate != "" {
		geoipData, err := fetch(geoipRepo, *geoipDate, ipName)
		if err != nil {
			log.Fatalln(err)
			return
		}
		/*metadata*/ _, countryMap, err := generateGeoip(geoipData)
		if err != nil {
			log.Fatalln(err)
			return
		}
		for countryCode, ipNets := range countryMap {
			var headlessRule option.DefaultHeadlessRule
			headlessRule.IPCIDR = make([]string, 0, len(ipNets))
			for _, cidr := range ipNets {
				headlessRule.IPCIDR = append(headlessRule.IPCIDR, cidr.String())
			}
			var plainRuleSet option.PlainRuleSet
			plainRuleSet.Rules = []option.HeadlessRule{
				{
					Type:           C.RuleTypeDefault,
					DefaultOptions: headlessRule,
				},
			}
			srsPath, _ := filepath.Abs(filepath.Join(geoipDir, "geoip-"+countryCode+".srs"))
			//_, _ = os.Stderr.WriteString("write " + srsPath + "\n")
			outputRuleSet, err := os.Create(srsPath)
			if err != nil {
				log.Fatalln(err)
				return
			}
			err = srs.Write(outputRuleSet, plainRuleSet, true)
			if err != nil {
				_ = outputRuleSet.Close()
				log.Fatalln(err)
				return
			}
			_ = outputRuleSet.Close()
		}
	}
}

func initDir(dir string) {
	_ = os.RemoveAll(dir)
	_ = os.MkdirAll(dir, os.ModePerm)

	geositeDir = filepath.Join(dir, site)
	geoipDir = filepath.Join(dir, ip)
	_ = os.MkdirAll(geositeDir, os.ModePerm)
	_ = os.MkdirAll(geoipDir, os.ModePerm)
}

func fetch(repo, tag, name string) ([]byte, error) {
	link := "https://github.com/" + repo + "/releases/download/" + tag + "/" + name

	resp, err := http.Get(link)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()

	return io.ReadAll(resp.Body)
}
