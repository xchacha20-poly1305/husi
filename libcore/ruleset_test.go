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
