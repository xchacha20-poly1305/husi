package libcore

import (
	"context"
	"io"
	"io/fs"
	"os"
	"path/filepath"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/srs"
	R "github.com/sagernet/sing-box/route/rule"
	M "github.com/sagernet/sing/common/metadata"
)

type ScanRuleSetCallback interface {
	Callback(path string)
}

// ScanRuleSet scans the rule set files for rules that match the given keyword.
// It traverses the directory specified by the combination of `externalAssetsPath` and `ruleSetPrefix`,
// reads each rule set file, and checks if any rule matches the provided keyword.
func ScanRuleSet(keyword string, callback ScanRuleSetCallback) error {
	err := filepath.WalkDir(filepath.Join(externalAssetsPath, ruleSetPrefix), func(path string, d fs.DirEntry, err error) (_ error) {
		if err != nil {
			return
		}
		file, err := os.Open(path)
		if err != nil {
			return
		}
		defer file.Close()
		ruleSet, err := srs.Read(file, false)
		if err != nil {
			return
		}
		plainRuleSet, err := ruleSet.Upgrade()
		if err != nil {
			return
		}
		ipAddress := M.ParseAddr(keyword)
		var metadata adapter.InboundContext
		if ipAddress.IsValid() {
			metadata.Destination = M.SocksaddrFrom(ipAddress, 0)
		} else {
			metadata.Domain = keyword
		}
		for _, ruleOptions := range plainRuleSet.Rules {
			var currentRule adapter.HeadlessRule
			currentRule, err = R.NewHeadlessRule(context.Background(), ruleOptions)
			if err != nil {
				continue
			}
			if currentRule.Match(&metadata) {
				callback.Callback(d.Name())
			}
		}
		return
	})
	if err != nil {
		return err
	}
	return nil
}

// IsSrs checks if a given file path points to a valid sing-box Rule Set (SRS) file.
func IsSrs(path string) bool {
	file, err := os.Open(path)
	if err != nil {
		return false
	}
	defer file.Close()
	var magic [len(srs.MagicBytes)]byte
	_, err = io.ReadFull(file, magic[:])
	if err != nil {
		return false
	}
	return magic == srs.MagicBytes
}
