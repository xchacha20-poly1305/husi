package libcore

import (
	"context"
	"io/fs"
	"os"
	"path/filepath"
	"slices"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/srs"
	"github.com/sagernet/sing-box/option"
	R "github.com/sagernet/sing-box/route/rule"
	M "github.com/sagernet/sing/common/metadata"
)

type ScanRuleSetCallback interface {
	Callback(path string)
}

// ScanRuleSet walks dir and reports the name of every binary rule set file
// that has a rule matching keyword. Unreadable files are skipped,
// and a missing dir reports nothing.
func ScanRuleSet(dir, keyword string, callback ScanRuleSetCallback) error {
	var metadata adapter.InboundContext
	if ipAddress := M.ParseAddr(keyword); ipAddress.IsValid() {
		metadata.Destination = M.SocksaddrFrom(ipAddress, 0)
	} else {
		metadata.Domain = keyword
	}
	return filepath.WalkDir(dir, func(path string, entry fs.DirEntry, err error) error {
		if err != nil || entry.IsDir() {
			return nil
		}
		if ruleSetFileMatches(path, &metadata) {
			callback.Callback(entry.Name())
		}
		return nil
	})
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
