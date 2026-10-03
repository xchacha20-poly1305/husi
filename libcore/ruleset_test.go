package libcore

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

type collectRuleSetNames []string

func (c *collectRuleSetNames) Callback(path string) {
	*c = append(*c, path)
}

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

func TestScanRuleSet(t *testing.T) {
	dir := t.TempDir()
	// Two matching rules in one file must still report the file once.
	writeDomainSuffixRuleSet(t, filepath.Join(dir, "geosite-google.srs"), []string{"google.com"}, []string{"com"})
	writeDomainSuffixRuleSet(t, filepath.Join(dir, "geosite-cn.srs"), []string{"cn"})
	require.NoError(t, os.WriteFile(filepath.Join(dir, "not-a-rule-set.txt"), []byte("google.com"), 0o600))

	var names collectRuleSetNames
	require.NoError(t, ScanRuleSet(dir, "www.google.com", &names))
	assert.Equal(t, collectRuleSetNames{"geosite-google.srs"}, names)
}

func TestScanRuleSetMissingDir(t *testing.T) {
	var names collectRuleSetNames
	assert.NoError(t, ScanRuleSet(filepath.Join(t.TempDir(), "missing"), "google.com", &names))
	assert.Empty(t, names)
}
