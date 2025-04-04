package main

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"flag"
	"io"
	"net/http"
	"os"

	"github.com/sagernet/sing-box/common/geosite"
	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"
)

var (
	geositeDate = flag.String("geosite", "", "geosite date")
	geoipDate   = flag.String("geoip", "", "geoip date")

	geositeOutput = flag.String("so", "geosite.tgz", "geosite tar.gz output")
	geoipOutput   = flag.String("io", "geoip.tgz", "geoip tar.gz output")
)

const (
	geositeRepo = "v2fly/domain-list-community"
	geoipRepo   = "Dreamacro/maxmind-geoip"

	siteName = "dlc.dat"
	ipName   = "Country.mmdb"

	finalBufCap = 524288
)

func init() {
	flag.Parse()
}

func main() {
	buf := bytes.NewBuffer(nil) // Shared buf.
	buf.Grow(finalBufCap)

	if *geositeDate != "" {
		siteFile, err := os.OpenFile(*geositeOutput, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, os.ModePerm)
		if err != nil {
			log.Fatal(err)
		}
		defer siteFile.Close()
		gWriter := common.Must1(gzip.NewWriterLevel(siteFile, gzip.BestCompression))
		defer gWriter.Close()
		tWriter := tar.NewWriter(gWriter)
		defer tWriter.Close()

		geositeData, err := fetch(geositeRepo, *geositeDate, siteName)
		if err != nil {
			log.Fatal(err)
		}
		geosites, err := generateGeosite(geositeData)
		if err != nil {
			log.Fatal(err)
		}
		for _, geositeItem := range geosites.Entries() {
			var headlessRule option.DefaultHeadlessRule
			defaultRule := geosite.Compile(geositeItem.Value)
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
			buf.Reset()
			err = srs.Write(buf, plainRuleSet, C.RuleSetVersionCurrent)
			if err != nil {
				log.Fatal(err)
			}
			srsName := "geosite-" + geositeItem.Key + ".srs"
			// Reproducible builds should not set time.
			err = tWriter.WriteHeader(&tar.Header{
				Name: srsName,
				Size: int64(buf.Len()),
				Mode: int64(os.ModePerm),
			})
			if err != nil {
				log.Fatal(err)
			}
			_, err = tWriter.Write(buf.Bytes())
			if err != nil {
				log.Fatal(err)
			}
		}
	}

	log.Trace("Buf length: ", buf.Len(), " cap: ", buf.Cap())

	if *geoipDate != "" {
		ipFile, err := os.OpenFile(*geoipOutput, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, os.ModePerm)
		if err != nil {
			log.Fatal(err)
		}
		defer ipFile.Close()
		gWriter := common.Must1(gzip.NewWriterLevel(ipFile, gzip.BestCompression))
		defer gWriter.Close()
		tWriter := tar.NewWriter(gWriter)
		defer tWriter.Close()

		geoipData, err := fetch(geoipRepo, *geoipDate, ipName)
		if err != nil {
			log.Fatal(err)
		}
		ips, err := parseGeoip(geoipData)
		if err != nil {
			log.Fatal(err)
		}
		for _, ip := range ips.Entries() {
			var headlessRule option.DefaultHeadlessRule
			headlessRule.IPCIDR = make([]string, 0, len(ip.Value))
			for _, cidr := range ip.Value {
				headlessRule.IPCIDR = append(headlessRule.IPCIDR, cidr.String())
			}
			var plainRuleSet option.PlainRuleSet
			plainRuleSet.Rules = []option.HeadlessRule{
				{
					Type:           C.RuleTypeDefault,
					DefaultOptions: headlessRule,
				},
			}
			buf.Reset()
			err = srs.Write(buf, plainRuleSet, C.RuleSetVersionCurrent)
			if err != nil {
				log.Fatal(err)
			}
			srsName := "geoip-" + ip.Key + ".srs"
			err = tWriter.WriteHeader(&tar.Header{
				Name: srsName,
				Size: int64(buf.Len()),
				Mode: int64(os.ModePerm),
			})
			if err != nil {
				log.Fatal(err)
			}
			_, err = tWriter.Write(buf.Bytes())
			if err != nil {
				log.Fatal(err)
			}
		}
	}

	log.Trace("Buf length: ", buf.Len(), " cap: ", buf.Cap())
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
