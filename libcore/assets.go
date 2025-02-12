package libcore

import (
	"bytes"
	"io"
	"os"
	"path/filepath"
	"time"

	"github.com/sagernet/gomobile/asset"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing/common"
	E "github.com/sagernet/sing/common/exceptions"
)

const (
	versionSuffix  = ".version.txt"
	geoipDat       = "geoip"
	geositeDat     = "geosite"
	geoipVersion   = geoipDat + versionSuffix
	geositeVersion = geositeDat + versionSuffix

	apkAssetPrefixSingBox = "sing-box/"
)

const (
	tgzSuffix      = ".tgz"
	geoipArchive   = geoipDat + tgzSuffix
	geositeArchive = geositeDat + tgzSuffix
)

var (
	internalAssetsPath string
	externalAssetsPath string
)

// extractAssets extract assets in apk.
func extractAssets(useOfficialAssets bool) {
	deleteDeprecated()

	if useOfficialAssets {
		// Prepare directory
		targetDir := filepath.Join(externalAssetsPath, "geo")
		_ = os.MkdirAll(targetDir, os.ModePerm)

		for _, name := range []string{geoipDat, geositeDat} {
			if err := extractGeo(name, targetDir); err != nil {
				log.Warn("failed to extract geo: ", err)
			}
		}
	}
}

func extractGeo(name, targetDir string) error {
	versionPath := filepath.Join(externalAssetsPath, name+versionSuffix)

	// Compare version
	assetsVersion, err := readAssetsVersion(filepath.Join(apkAssetPrefixSingBox, name+versionSuffix))
	if err != nil || len(assetsVersion) < 1 {
		assetsVersion = []byte(time.Now().Format("20060102"))
	}
	localVersion, err := os.ReadFile(versionPath)
	if err == nil {
		if bytes.Compare(assetsVersion, localVersion) <= 0 {
			return nil
		}
	}

	// Unpack
	assetFile, err := asset.Open(filepath.Join(apkAssetPrefixSingBox, name) + tgzSuffix)
	if err != nil {
		return E.Cause(err, "open asset file ", name)
	}
	tmpPackName := filepath.Join(targetDir, name) + tgzSuffix
	if err = extractAssetToFile(assetFile, tmpPackName); err != nil {
		return E.Cause(err, "copy tmp file of ", name)
	}

	_ = removeIfHasPrefix(targetDir, name+"-")
	err = UntargzWithoutDir(tmpPackName, targetDir)
	_ = os.Remove(tmpPackName)
	if err != nil {
		return E.Cause(err, "untargz ", name)
	}

	_ = os.Remove(versionPath)
	if err = os.WriteFile(versionPath, assetsVersion, os.ModePerm); err != nil {
		return E.Cause(err, "write version ", name)
	}

	return nil
}

// readAssetsVersion read the version from file in assets.
func readAssetsVersion(name string) ([]byte, error) {
	assetFile, err := asset.Open(name)
	if err != nil {
		return nil, err
	}
	defer assetFile.Close()
	return io.ReadAll(assetFile)
}

func extractAssetToFile(assetFile asset.File, path string) error {
	defer assetFile.Close()

	targetFile, err := os.Create(path)
	if err != nil {
		return err
	}
	defer targetFile.Close()

	err = common.Error(io.Copy(targetFile, assetFile))
	if err == nil {
		log.Info("Extract >>", path)
	}
	return err
}

// deleteDeprecated deletes the unused file in current version.
// Now will delete:
//
// Clash dashboard,
//
// "geo/pax_global_header", which was added by invalid untargz implementation.
func deleteDeprecated() {
	dashboardPath := filepath.Join(internalAssetsPath, "dashboard")
	_ = os.RemoveAll(dashboardPath)
	_ = os.Remove(dashboardPath + tgzSuffix)
	_ = os.Remove(dashboardPath + versionSuffix)

	_ = os.Remove(filepath.Join(externalAssetsPath, "geo", "pax_global_header"))
}
