package main

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"context"
	"flag"
	"io"
	"net"
	"net/http"
	"net/netip"
	"os"
	"strings"

	"github.com/sagernet/sing-box/common/geosite"
	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json/badoption"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"

	"github.com/xchacha20-poly1305/husi/libcore/v2/simpleproxyurl"
)

var (
	geositeDate = flag.String("geosite", "", "domain-list-community ref")
	geoipDate   = flag.String("geoip", "", "geoip date")

	geositeOutput = flag.String("so", "geosite.tgz", "geosite tar.gz output")
	geoipOutput   = flag.String("io", "geoip.tgz", "geoip tar.gz output")
)

const (
	geositeRepo = "v2fly/domain-list-community"
	geoipRepo   = "Dreamacro/maxmind-geoip"

	ipName = "Country.mmdb"

	finalBufCap = 524288
)

func main() {
	flag.Parse()

	buffer := bytes.NewBuffer(nil) // Shared buf.
	buffer.Grow(finalBufCap)

	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()
	dialer, err := simpleproxyurl.DialerFromEnv(ctx, nil)
	if err != nil {
		log.WarnContext(ctx, err)
		dialer = N.SystemDialer
	}
	httpClient := &http.Client{
		Transport: &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				return dialer.DialContext(ctx, network, M.ParseSocksaddr(addr))
			},
		},
		Timeout: C.TCPTimeout * 2,
	}

	if *geositeDate != "" {
		if err := generateGeositeArchive(ctx, httpClient, buffer); err != nil {
			log.FatalContext(ctx, err)
		}
	}

	log.TraceContext(ctx, "Buf length: ", buffer.Len(), " cap: ", buffer.Cap())

	if *geoipDate != "" {
		if err := generateGeoipArchive(ctx, httpClient, buffer); err != nil {
			log.FatalContext(ctx, err)
		}
	}

	log.TraceContext(ctx, "Buf length: ", buffer.Len(), " cap: ", buffer.Cap())
}

func generateGeositeArchive(ctx context.Context, httpClient *http.Client, buffer *bytes.Buffer) error {
	siteFile, err := os.Create(*geositeOutput)
	if err != nil {
		return err
	}
	defer siteFile.Close()
	gzipWriter, err := newGzipWriter(siteFile)
	if err != nil {
		return err
	}
	defer gzipWriter.Close()
	tWriter := tar.NewWriter(gzipWriter)
	defer tWriter.Close()

	geositeArchive, err := fetchGitHubArchive(ctx, httpClient, geositeRepo, *geositeDate)
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

func generateGeoipArchive(ctx context.Context, httpClient *http.Client, buf *bytes.Buffer) error {
	ipFile, err := os.Create(*geoipOutput)
	if err != nil {
		return err
	}
	defer ipFile.Close()
	gzipWriter, err := newGzipWriter(ipFile)
	if err != nil {
		return err
	}
	defer gzipWriter.Close()
	tWriter := tar.NewWriter(gzipWriter)
	defer tWriter.Close()

	geoipData, err := fetchRelease(ctx, httpClient, geoipRepo, *geoipDate, ipName)
	if err != nil {
		return err
	}
	ips, err := parseGeoip(geoipData)
	if err != nil {
		return err
	}
	for _, ip := range ips.Entries() {
		var headlessRule option.DefaultHeadlessRule
		headlessRule.IPCIDR = common.Map(ip.Value, func(it netip.Prefix) *badoption.Prefixable {
			return new(badoption.Prefixable(it))
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

func fetchRelease(ctx context.Context, httpClient *http.Client, repo, tag, name string) ([]byte, error) {
	link := "https://github.com/" + repo + "/releases/download/" + tag + "/" + name

	request, err := http.NewRequestWithContext(ctx, http.MethodGet, link, nil)
	if err != nil {
		return nil, E.Cause(err, "build http request")
	}
	resp, err := httpClient.Do(request)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, E.New("fetch ", link, ": ", resp.Status)
	}

	return io.ReadAll(resp.Body)
}

func fetchGitHubArchive(ctx context.Context, httpClient *http.Client, repo, ref string) (io.ReadCloser, error) {
	if !strings.HasPrefix(ref, "refs/") {
		ref = "refs/tags/" + ref
	}
	link := "https://codeload.github.com/" + repo + "/tar.gz/" + ref

	request, err := http.NewRequestWithContext(ctx, http.MethodGet, link, nil)
	if err != nil {
		return nil, E.Cause(err, "build http request")
	}
	resp, err := httpClient.Do(request)
	if err != nil {
		return nil, err
	}
	if resp.StatusCode != http.StatusOK {
		defer resp.Body.Close()
		return nil, E.New("fetch ", link, ": ", resp.Status)
	}

	return resp.Body, nil
}

// newGzipWriter compresses an archive the app unpacks with the JVM's own
// inflater. The .srs entries are zlib streams already, so gzip comes within a
// few percent of zstd here.
func newGzipWriter(writer io.Writer) (*gzip.Writer, error) {
	return gzip.NewWriterLevel(writer, gzip.BestCompression)
}
