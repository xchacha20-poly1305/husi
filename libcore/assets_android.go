//go:build android

package libcore

import (
	"io"
	"log"
	"os"
	"path/filepath"
	"time"

	E "github.com/sagernet/sing/common/exceptions"
	"golang.org/x/mobile/asset"
)

// 支持非官方源的，放 Android 目录
// 不支持非官方源的，就放 file 目录
func extractAssets() {
	// TODO avoid unofficial assets
	useOfficialAssets := intfNB4A.UseOfficialAssets()

	// 解压的是 apk 里面的 assets

	err := extractGeo(useOfficialAssets)
	if err != nil {
		log.Println(err)
	}
	err = extractDash(useOfficialAssets)
	if err != nil {
		log.Println(err)
	}
}

func extractGeo(useOfficialAssets bool) error {
	names := []string{geoipDat, geositeDat}
	versions := []string{geoipVersion, geositeVersion}
	dir := externalAssetsPath
	dstName := filepath.Join(dir, "geo")
	apkPrefix := apkAssetPrefixSingBox

	// load assets version
	var assetsVersions [][]byte
	for _, version := range versions {
		assetsVersion, err := readAssetsVersion(filepath.Join(apkPrefix, version))
		if err != nil || len(assetsVersion) < 1 {
			assetsVersions = append(assetsVersions,
				[]byte(time.Now().Format("20060102")))
			continue
		}
		assetsVersions = append(assetsVersions, assetsVersion)
	}

	// Prepare directory
	os.RemoveAll(dstName)
	os.MkdirAll(dstName, os.ModePerm)

	// Unzip geoip and geosite
	for _, name := range names {
		file, err := asset.Open(filepath.Join(apkPrefix, name) + ".zip")
		if err != nil {
			return E.Cause(err, "open asset", name)
		}

		tmpZipName := filepath.Join(dstName, name) + ".zip"
		err = extractAsset(file, tmpZipName)
		if err != nil {
			return E.Cause(err, "extract:", name)
		}

		err = UnzipWithoutDir(tmpZipName, dstName)
		os.Remove(tmpZipName)
		if err != nil {
			return E.Cause(err, "unzip:", name)
		}
	}

	// write version
	for i, version := range versions {
		err := writeVersion(assetsVersions[i],
			filepath.Join(dir, version))
		if err != nil {
			return err
		}
	}

	return nil
}

func extractDash(useOfficialAssets bool) error {
	name := dashDstFolder
	version := dashVersion
	dir := internalAssetsPath
	dstName := filepath.Join(dir, name)

	assetsVersion, err := readAssetsVersion(version)
	if err != nil || len(assetsVersion) < 1 {
		assetsVersion = []byte(time.Now().Format("20060102"))
	}

	os.RemoveAll(dstName)

	file, err := asset.Open(dashArchive)
	if err != nil {
		return E.Cause(err, "can't open", dashArchive)
	}
	tmpZipName := dstName + ".zip"
	err = extractAsset(file, tmpZipName)
	if err != nil {
		return E.Cause(err, "extract:", tmpZipName)
	}
	err = Unzip(tmpZipName, dir)
	if err != nil {
		return E.Cause(err, "unzip")
	}

	// find multiplex files
	multiFile, err := filepath.Glob(filepath.Join(dir, "Dash-*"))
	if err != nil {
		return E.Cause(err, "glob dashboard")
	}
	// delete more dashboards
	for i := 1; i < len(multiFile); i++ {
		os.RemoveAll(multiFile[i])
	}
	err = os.Rename(multiFile[0], dstName)
	if err != nil {
		return E.Cause(err, "rename dashboard")
	}

	err = writeVersion(assetsVersion, filepath.Join(dir, version))
	if err != nil {
		return err
	}

	return nil
}

func readAssetsVersion(path string) ([]byte, error) {
	av, err := asset.Open(path)
	if err != nil {
		return nil, err
	}
	defer av.Close()
	return io.ReadAll(av)
}

func writeVersion(version []byte, versionPath string) error {
	os.Remove(versionPath)

	versionFile, err := os.Create(versionPath)
	if err != nil {
		return E.Cause(err, "create version file:", versionPath)
	}
	_, err = versionFile.Write(version)
	versionFile.Close()
	if err != nil {
		return E.Cause(err, "write version:", version)
	}

	return nil
}

func extractAsset(i asset.File, path string) error {
	defer i.Close()
	o, err := os.Create(path)
	if err != nil {
		return err
	}
	defer o.Close()
	_, err = io.Copy(o, i)
	if err == nil {
		log.Println("Extract >>", path)
	}
	return err
}
