package main

import (
	"archive/tar"
	"bytes"
	"flag"
	"io"
	"net"
	"net/http"
	"os"
	"strings"

	"github.com/sagernet/sing-box/common/geosite"
	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/klauspost/compress/zstd"
)

var (
	geositeDate = flag.String("geosite", "", "domain-list-community ref")
	geoipDate   = flag.String("geoip", "", "geoip date")

	geositeOutput = flag.String("so", "geosite.tar.zst", "geosite tar.zst output")
	geoipOutput   = flag.String("io", "geoip.tar.zst", "geoip tar.zst output")
)

const (
	geositeRepo = "v2fly/domain-list-community"
	geoipRepo   = "Dreamacro/maxmind-geoip"

	ipName = "Country.mmdb"

	finalBufCap = 524288

	// windowSize defines the zstd compression window size.
	// Using 128KB instead of MaxWindowSize (1GB) to avoid OOM on low-memory devices during decompression.
	//
	// Analysis of actual file sizes in tar archives:
	//   geoip.tar:   237 files, median 633B,  95th percentile 28.62KB,  max 259KB
	//   geosite.tar: 1683 files, median 94B, 95th percentile 1.09KB, max 182KB
	//
	// Since .srs files are already compressed with zlib.BestCompression, they contain high-entropy
	// data that cannot be significantly re-compressed. The 1GB window searches through massive amounts
	// of effectively random data, providing no benefit over a smaller window while requiring 1GB+ memory.
	//
	// 128KB window is sufficient to:
	//   - Cover 4.5x the 95th percentile file size
	//   - Capture patterns across 23-222 average files
	//   - Compress tar headers and metadata effectively
	//   - Decompress with only ~256KB memory (128KB window + 128KB max block)
	//
	// See: https://github.com/xchacha20-poly1305/husi/issues/614
	//      https://github.com/klauspost/compress/discussions/675
	windowSize = 128 << 10 // 128KB
)

func main() {
	flag.Parse()

	buffer := bytes.NewBuffer(nil) // Shared buf.
	buffer.Grow(finalBufCap)

	if *geositeDate != "" {
		if err := generateGeositeArchive(buffer); err != nil {
			log.Fatal(err)
		}
	}

	log.Trace("Buf length: ", buffer.Len(), " cap: ", buffer.Cap())

	if *geoipDate != "" {
		if err := generateGeoipArchive(buffer); err != nil {
			log.Fatal(err)
		}
	}

	log.Trace("Buf length: ", buffer.Len(), " cap: ", buffer.Cap())
}

func generateGeositeArchive(buffer *bytes.Buffer) error {
	siteFile, err := os.Create(*geositeOutput)
	if err != nil {
		return err
	}
	defer siteFile.Close()
	zWriter, err := newZstdWriter(siteFile)
	if err != nil {
		return err
	}
	defer zWriter.Close()
	tWriter := tar.NewWriter(zWriter)
	defer tWriter.Close()

	geositeArchive, err := fetchGitHubArchive(geositeRepo, *geositeDate)
	if err != nil {
		return err
	}
	defer geositeArchive.Close()
	geosites, err := generateGeosite(geositeArchive)
	if err != nil {
		return err
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
		buffer.Reset()
		err = srs.Write(buffer, plainRuleSet, C.RuleSetVersionCurrent)
		if err != nil {
			return err
		}
		srsName := "geosite-" + geositeItem.Key + ".srs"
		// Reproducible builds should not set time.
		err = tWriter.WriteHeader(&tar.Header{
			Name: srsName,
			Size: int64(buffer.Len()),
			Mode: int64(os.ModePerm),
		})
		if err != nil {
			return err
		}
		_, err = tWriter.Write(buffer.Bytes())
		if err != nil {
			return err
		}
	}
	return nil
}

func generateGeoipArchive(buf *bytes.Buffer) error {
	ipFile, err := os.Create(*geoipOutput)
	if err != nil {
		return err
	}
	defer ipFile.Close()
	zWriter, err := newZstdWriter(ipFile)
	if err != nil {
		return err
	}
	defer zWriter.Close()
	tWriter := tar.NewWriter(zWriter)
	defer tWriter.Close()

	geoipData, err := fetchRelease(geoipRepo, *geoipDate, ipName)
	if err != nil {
		return err
	}
	ips, err := parseGeoip(geoipData)
	if err != nil {
		return err
	}
	for _, ip := range ips.Entries() {
		var headlessRule option.DefaultHeadlessRule
		headlessRule.IPCIDR = common.Map(ip.Value, func(it *net.IPNet) string {
			return it.String()
		})
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
			return err
		}
		srsName := "geoip-" + ip.Key + ".srs"
		err = tWriter.WriteHeader(&tar.Header{
			Name: srsName,
			Size: int64(buf.Len()),
			Mode: int64(os.ModePerm),
		})
		if err != nil {
			return err
		}
		_, err = tWriter.Write(buf.Bytes())
		if err != nil {
			return err
		}
	}
	return nil
}

func fetchRelease(repo, tag, name string) ([]byte, error) {
	link := "https://github.com/" + repo + "/releases/download/" + tag + "/" + name

	resp, err := http.Get(link)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, E.New("fetch ", link, ": ", resp.Status)
	}

	return io.ReadAll(resp.Body)
}

func fetchGitHubArchive(repo, ref string) (io.ReadCloser, error) {
	if !strings.HasPrefix(ref, "refs/") {
		ref = "refs/tags/" + ref
	}
	link := "https://codeload.github.com/" + repo + "/tar.gz/" + ref

	resp, err := http.Get(link)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		defer resp.Body.Close()
		return nil, E.New("fetch ", link, ": ", resp.Status)
	}

	return resp.Body, nil
}

func newZstdWriter(writer io.Writer) (*zstd.Encoder, error) {
	return zstd.NewWriter(
		writer,
		zstd.WithEncoderLevel(zstd.SpeedBestCompression),
		zstd.WithWindowSize(windowSize),
	)
}
