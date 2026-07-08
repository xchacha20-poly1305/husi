package main

import (
	"archive/tar"
	"bytes"
	"compress/gzip"
	"testing"

	"github.com/sagernet/sing-box/common/geosite"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestGenerateGeositeDomainMapFromSourceArchive(t *testing.T) {
	archiveData := makeGeositeArchive(t, map[string]string{
		"domain-list-community-test/data/base": `
example.com
full:api.example.com
keyword:ads @ads
regexp:^foo\.example$ @cn
example.cn @cn &affiliate
`,
		"domain-list-community-test/data/child": `
include:base @cn
child.com
`,
	})

	processor, err := parseGeositeSource(bytes.NewReader(archiveData))
	require.NoError(t, err)
	domainMap, err := processor.generateDomainMap()
	require.NoError(t, err)

	base, _ := domainMap.Get("base")
	assertHasGeositeItem(t, base, geosite.RuleTypeDomain, "example.com")
	assertHasGeositeItem(t, base, geosite.RuleTypeDomainSuffix, ".example.com")
	assertHasGeositeItem(t, base, geosite.RuleTypeDomainKeyword, "ads")
	assertHasGeositeItem(t, base, geosite.RuleTypeDomainRegex, `^foo\.example$`)
	assertNoGeositeItem(t, base, geosite.RuleTypeDomain, "api.example.com")

	child, _ := domainMap.Get("child")
	assertHasGeositeItem(t, child, geosite.RuleTypeDomain, "child.com")
	assertHasGeositeItem(t, child, geosite.RuleTypeDomainRegex, `^foo\.example$`)
	assertHasGeositeItem(t, child, geosite.RuleTypeDomain, "example.cn")
	assertNoGeositeItem(t, child, geosite.RuleTypeDomainKeyword, "ads")

	affiliate, _ := domainMap.Get("affiliate")
	assertHasGeositeItem(t, affiliate, geosite.RuleTypeDomain, "example.cn")

	baseCN, _ := domainMap.Get("base@cn")
	assertHasGeositeItem(t, baseCN, geosite.RuleTypeDomainRegex, `^foo\.example$`)
	assertHasGeositeItem(t, baseCN, geosite.RuleTypeDomain, "example.cn")
}

func makeGeositeArchive(t *testing.T, files map[string]string) []byte {
	t.Helper()

	var buffer bytes.Buffer
	gzipWriter := gzip.NewWriter(&buffer)
	tarWriter := tar.NewWriter(gzipWriter)
	for name, content := range files {
		if err := tarWriter.WriteHeader(&tar.Header{
			Name: name,
			Mode: 0o644,
			Size: int64(len(content)),
		}); err != nil {
			require.NoError(t, err)
		}
		if _, err := tarWriter.Write([]byte(content)); err != nil {
			require.NoError(t, err)
		}
	}
	if err := tarWriter.Close(); err != nil {
		require.NoError(t, err)
	}
	if err := gzipWriter.Close(); err != nil {
		require.NoError(t, err)
	}
	return buffer.Bytes()
}

func assertHasGeositeItem(t *testing.T, items []geosite.Item, itemType uint8, value string) {
	t.Helper()

	for _, item := range items {
		if item.Type == itemType && item.Value == value {
			return
		}
	}
	require.Failf(t, "missing geosite item", "type=%d value=%q in %#v", itemType, value, items)
}

func assertNoGeositeItem(t *testing.T, items []geosite.Item, itemType uint8, value string) {
	t.Helper()

	for _, item := range items {
		assert.Falsef(t, item.Type == itemType && item.Value == value, "unexpected geosite item type=%d value=%q in %#v", itemType, value, items)
	}
}
