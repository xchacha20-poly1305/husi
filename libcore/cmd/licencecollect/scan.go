package main

import (
	"context"
	"fmt"
	"os"
	"path/filepath"
	"slices"
	"strings"

	"github.com/sagernet/sing-box/log"
	E "github.com/sagernet/sing/common/exceptions"

	"github.com/google/licensecheck"
)

const (
	coverageThreshold = 75.0

	maxScannedFileSize = 10 << 20
)

var licenseFileNames = buildLicenseFileNames()

func buildLicenseFileNames() map[string]bool {
	stems := []string{"LICENSE", "LICENCE", "COPYING", "NOTICE"}
	extensions := []string{"", ".MD", ".MARKDOWN", ".TXT"}
	names := make(map[string]bool, len(stems)*len(extensions))
	for _, stem := range stems {
		for _, extension := range extensions {
			names[stem+extension] = true
		}
	}
	return names
}

func isLicenseFileName(name string) bool {
	return licenseFileNames[strings.ToUpper(name)]
}

func scanModuleLicenses(ctx context.Context, dir string) ([]string, error) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return nil, err
	}

	var licenses []string
	for _, entry := range entries {
		if entry.IsDir() || !isLicenseFileName(entry.Name()) {
			continue
		}
		licenseFile := filepath.Join(dir, entry.Name())
		info, err := entry.Info()
		if err != nil {
			return nil, err
		}
		if info.Size() > maxScannedFileSize {
			log.WarnContext(ctx, "skip oversized license file ", licenseFile)
			continue
		}
		content, err := os.ReadFile(licenseFile)
		if err != nil {
			return nil, err
		}
		coverage := licensecheck.Scan(content)
		if coverage.Percent < coverageThreshold {
			log.WarnContext(ctx, "skip ", licenseFile, ": only ", coverage.Percent, "% matches known license text")
			continue
		}
		for _, match := range coverage.Match {
			licenses = append(licenses, match.ID)
		}
	}
	if len(licenses) == 0 {
		return nil, E.New("no recognizable license file in ", dir)
	}

	slices.Sort(licenses)
	return slices.Clip(slices.Compact(licenses)), nil
}

var licenseOverrides = map[string][]string{
	"github.com/exclavenetwork/sing-juicity": {"GPL-3.0-or-later"}, // "either version 3 of the License, or (at your option) any later version"
}

func overrideLicenses(ctx context.Context, modulePath string, scanned []string) []string {
	override, hasOverride := licenseOverrides[modulePath]
	if !hasOverride {
		return scanned
	}
	if slices.Equal(scanned, override) {
		log.WarnContext(ctx, "license override for ", modulePath, " is redundant, drop it")
		return scanned
	}
	log.InfoContext(ctx, "override licenses of ", modulePath, ": scanned ", fmt.Sprint(scanned), ", declared ", fmt.Sprint(override))
	return override
}
