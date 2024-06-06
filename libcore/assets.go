package libcore

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
