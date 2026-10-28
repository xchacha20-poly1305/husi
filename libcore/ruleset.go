package libcore

import (
	"context"
	"io/fs"
	"os"
	"path/filepath"
	"slices"
	"strings"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/srs"
	"github.com/sagernet/sing-box/option"
	R "github.com/sagernet/sing-box/route/rule"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"

	"golang.org/x/text/language"
)

// matchRuleSets walks dir and returns the name of every binary rule set file
// that has a rule matching keyword. Unreadable files are skipped,
// and a missing dir reports nothing.
func matchRuleSets(dir, keyword string) []string {
	var metadata adapter.InboundContext
	if ipAddress := M.ParseAddr(keyword); ipAddress.IsValid() {
		metadata.Destination = M.SocksaddrFrom(ipAddress, 0)
	} else {
		metadata.Domain = keyword
	}
	var names []string
	_ = filepath.WalkDir(dir, func(path string, entry fs.DirEntry, err error) error {
		if err != nil || entry.IsDir() {
			return nil
		}
		if ruleSetFileMatches(path, &metadata) {
			names = append(names, entry.Name())
		}
		return nil
	})
	return names
}

func ruleSetFileMatches(path string, metadata *adapter.InboundContext) bool {
	file, err := os.Open(path)
	if err != nil {
		return false
	}
	defer file.Close()
	ruleSet, err := srs.Read(file, false)
	if err != nil {
		return false
	}
	plainRuleSet, err := ruleSet.Upgrade()
	if err != nil {
		return false
	}
	return slices.ContainsFunc(plainRuleSet.Rules, func(ruleOptions option.HeadlessRule) bool {
		rule, err := R.NewHeadlessRule(context.Background(), ruleOptions)
		if err != nil {
			return false
		}
		metadata.ResetRuleMatchCache()
		return rule.Match(metadata)
	})
}

func lookupIPCountry(ctx context.Context, dir, ip string) (string, error) {
	address := M.ParseAddr(ip)
	if !address.IsValid() {
		return "", E.New("invalid IP address")
	}
	// os.ReadDir sorts the entries
	entries, err := os.ReadDir(dir)
	if os.IsNotExist(err) {
		return "", nil
	}
	if err != nil {
		return "", err
	}
	metadata := adapter.InboundContext{Destination: M.SocksaddrFrom(address.Unmap(), 0)}
	for _, entry := range entries {
		if err := ctx.Err(); err != nil {
			return "", err
		}
		name := entry.Name()
		if entry.IsDir() {
			continue
		}
		code := name
		code, found := strings.CutPrefix(code, "geoip-")
		if !found {
			continue
		}
		code, found = strings.CutSuffix(code, ".srs")
		if !found {
			continue
		}
		if len(code) != 2 {
			continue
		}
		region, err := language.ParseRegion(code)
		if err != nil || !region.IsCountry() {
			continue
		}
		if ruleSetFileMatches(filepath.Join(dir, name), &metadata) {
			return region.String(), nil
		}
	}
	return "", nil
}
