//go:build android

package libcore

import (
	"io"
	"log"
	"os"
	"path/filepath"
	"strconv"
	"time"

	"github.com/sagernet/gomobile/asset"
	E "github.com/sagernet/sing/common/exceptions"
)

// 支持非官方源的，放 Android 目录 (dashboard)
// 不支持非官方源的，就放 file 目录 (geo)
// 解压的是 apk 里面的 assets
func extractAssets() {
	useOfficialAssets := intfNB4A.UseOfficialAssets()

	if useOfficialAssets {
		err := extractGeo()
		if err != nil {
			log.Println(err)
		}
	}

	err := extractDash()
	if err != nil {
		log.Println(err)
	}
}

// extract geo files.
func extractGeo() error {
	names := []string{geoipDat, geositeDat}
	versionPaths := []string{geoipVersion, geositeVersion}
	dir := externalAssetsPath
	targetDir := filepath.Join(dir, "geo")
	assetsDir := apkAssetPrefixSingBox

	// load assets version
	var assetsVersions [][]byte
	for _, versionPath := range versionPaths {
		assetsVersion, err := readAssetsVersion(filepath.Join(assetsDir, versionPath))
		if err != nil || len(assetsVersion) < 1 {
			assetsVersions = append(assetsVersions,
				[]byte(time.Now().Format("20060102")))
			continue
		}
		assetsVersions = append(assetsVersions, assetsVersion)
	}

	// compare version
	needUpdate := true
	for i, assetsVersion := range assetsVersions {
		localVersion, err := readLocalVersion(filepath.Join(dir, versionPaths[i]))
		if err != nil {
			// miss local versionPath
			break
		}
		// needn't update
		if !versionLess(localVersion, assetsVersion) {
			needUpdate = false
		}
	}
	if !needUpdate {
		return nil
	}

	// Prepare directory
	os.RemoveAll(targetDir)
	os.MkdirAll(targetDir, os.ModePerm)

	// Unzip geoip and geosite
	for _, name := range names {
		file, err := asset.Open(filepath.Join(assetsDir, name) + ".zip")
		if err != nil {
			return E.Cause(err, "open asset", name)
		}

		tmpZipName := filepath.Join(targetDir, name) + ".zip"
		err = extractAsset(file, tmpZipName)
		if err != nil {
			return E.Cause(err, "extract:", name)
		}

		err = UnzipWithoutDir(tmpZipName, targetDir)
		os.Remove(tmpZipName)
		if err != nil {
			return E.Cause(err, "unzip:", name)
		}
	}

	// write version
	for i, version := range versionPaths {
		err := writeVersion(assetsVersions[i],
			filepath.Join(dir, version))
		if err != nil {
			return err
		}
	}

	return nil
}

// Extract dashboard
func extractDash() error {
	name := dashDstFolder
	versionPath := dashVersion
	dir := internalAssetsPath
	dstName := filepath.Join(dir, name)

	// load assets version
	assetsVersion, err := readAssetsVersion(versionPath)
	if err != nil || len(assetsVersion) < 1 {
		assetsVersion = []byte(time.Now().Format("20060102"))
	}

	// compare version
	localVersion, err := readLocalVersion(filepath.Join(dir, versionPath))
	if err == nil {
		// needn't update
		if !versionLess(localVersion, assetsVersion) {
			return nil
		}
	}

	os.RemoveAll(dstName)

	// unzip file
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

	err = writeVersion(assetsVersion, filepath.Join(dir, versionPath))
	if err != nil {
		return err
	}

	return nil
}

// Read the version file in assets
func readAssetsVersion(path string) ([]byte, error) {
	av, err := asset.Open(path)
	if err != nil {
		return nil, err
	}
	defer av.Close()
	return io.ReadAll(av)
}

// Read the local assets name
func readLocalVersion(path string) ([]byte, error) {
	return os.ReadFile(path)
}

// Compare version
func versionLess(localVersion, assetsVersion []byte) bool {
	localInt, _ := strconv.Atoi(string(localVersion))
	assetsInt, _ := strconv.Atoi(string(assetsVersion))
	return localInt < assetsInt
}

// Write version to version file
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

// Extract the file in asset
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
