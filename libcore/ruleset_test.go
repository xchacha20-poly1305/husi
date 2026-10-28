package libcore

import (
	"context"
	"net/netip"
	"os"
	"path/filepath"
	"testing"

	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"
	"github.com/sagernet/sing/common/json/badoption"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func writeDomainSuffixRuleSet(t *testing.T, path string, suffixGroups ...[]string) {
	t.Helper()
	ruleSet := option.PlainRuleSet{
		Rules: common.Map(suffixGroups, func(it []string) option.HeadlessRule {
			return option.HeadlessRule{
				Type: C.RuleTypeDefault,
				DefaultOptions: option.DefaultHeadlessRule{
					DomainSuffix: it,
				},
			}
		}),
	}
	file, err := os.Create(path)
	require.NoError(t, err)
	defer file.Close()
	require.NoError(t, srs.Write(file, ruleSet, C.RuleSetVersionCurrent))
}

func TestMatchRuleSets(t *testing.T) {
	dir := t.TempDir()
	nestedDir := filepath.Join(dir, "nested")
	require.NoError(t, os.Mkdir(nestedDir, 0o755))

	// Two matching rules in one file must still report the file once.
	writeDomainSuffixRuleSet(t, filepath.Join(dir, "geosite-google.srs"), []string{"google.com"}, []string{"com"})
	writeDomainSuffixRuleSet(t, filepath.Join(dir, "geosite-cn.srs"), []string{"cn"})
	writeDomainSuffixRuleSet(t, filepath.Join(nestedDir, "custom-google.srs"), []string{"google.com"})
	require.NoError(t, os.WriteFile(filepath.Join(dir, "not-a-rule-set.txt"), []byte("google.com"), 0o644))

	assert.ElementsMatch(t, []string{"geosite-google.srs", "custom-google.srs"}, matchRuleSets(dir, "www.google.com"))
}

func TestMatchRuleSetsMissingDirectory(t *testing.T) {
	assert.Empty(t, matchRuleSets(filepath.Join(t.TempDir(), "missing"), "google.com"))
}

func writeCountryRuleSet(t *testing.T, path string, cidr string) {
	t.Helper()
	prefix := badoption.Prefixable(netip.MustParsePrefix(cidr))
	rules := option.PlainRuleSet{Rules: []option.HeadlessRule{{
		Type:           C.RuleTypeDefault,
		DefaultOptions: option.DefaultHeadlessRule{IPCIDR: []*badoption.Prefixable{&prefix}},
	}}}
	file, err := os.Create(path)
	require.NoError(t, err)
	defer file.Close()
	require.NoError(t, srs.Write(file, rules, C.RuleSetVersionCurrent))
}

func TestLookupIPCountry(t *testing.T) {
	dir := t.TempDir()
	// Distractor
	for _, name := range []string{"geoip-aa.srs", "geoip-01.srs", "geoip-private.srs", "geosite-us.srs"} {
		writeCountryRuleSet(t, filepath.Join(dir, name), "0.0.0.0/0")
	}
	writeCountryRuleSet(t, filepath.Join(dir, "geoip-au.srs"), "203.0.113.0/24")
	writeCountryRuleSet(t, filepath.Join(dir, "geoip-us.srs"), "203.0.113.0/24")
	writeCountryRuleSet(t, filepath.Join(dir, "geoip-jp.srs"), "2001:db8::/32")
	for _, test := range []struct {
		ip   string
		code string
	}{
		{"203.0.113.7", "AU"},
		{"2001:db8::7", "JP"},
		{"192.0.2.7", ""},
	} {
		code, err := lookupIPCountry(t.Context(), dir, test.ip)
		require.NoError(t, err)
		require.Equal(t, test.code, code)
	}
	_, err := lookupIPCountry(t.Context(), dir, "not an IP")
	require.Error(t, err)
	ctx, cancel := context.WithCancel(t.Context())
	cancel()
	_, err = lookupIPCountry(ctx, dir, "203.0.113.7")
	require.ErrorIs(t, err, context.Canceled)
	code, err := lookupIPCountry(t.Context(), filepath.Join(dir, "missing"), "203.0.113.7")
	require.NoError(t, err)
	require.Empty(t, code)
}
