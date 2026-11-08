package fr.husi.utils

import fr.husi.bg.routeGeoDir
import fr.husi.ktx.Logs
import fr.husi.ktx.unpackArchive
import fr.husi.repository.resolveRepository
import java.io.File
import java.io.InputStream

private const val BUNDLED_RULE_SET_BASE = "composeResources/fr.husi.resources/files/sing-box"
private val RULE_SET_NAMES = listOf("geoip", "geosite")
private const val VERSION_SUFFIX = ".version.txt"

/**
 * Not ".tar.gz": AGP gunzips "*.gz" assets and strips the suffix when packaging the APK,
 * so a ".tar.gz" name would not exist on Android.
 */
private const val ARCHIVE_SUFFIX = ".tgz"

/** Where older versions staged the bundled archives before Go unpacked them. */
private const val LEGACY_STAGING_DIR_NAME = "sing-box"

/** Opens a file bundled with the app, or returns null when it is not bundled. */
internal expect fun openBundledResource(path: String): InputStream?

/**
 * Unpacks the bundled geoip/geosite rule sets into the route geo directory when the bundled
 * version is newer than the installed one, or none is installed.
 */
internal fun installBundledRuleSets() {
    val repository = resolveRepository()
    val externalAssetsDir = repository.externalAssetsDir
    val geoDir = routeGeoDir(externalAssetsDir)
    repository.filesDir.resolve(LEGACY_STAGING_DIR_NAME).deleteRecursively()

    for (name in RULE_SET_NAMES) {
        try {
            installBundledRuleSet(name, externalAssetsDir, geoDir)
        } catch (e: Exception) {
            Logs.w("install bundled rule set $name", e)
        }
    }
}

private fun installBundledRuleSet(name: String, externalAssetsDir: File, geoDir: File) {
    val bundledVersion = openBundledResource("$BUNDLED_RULE_SET_BASE/$name$VERSION_SUFFIX")
        ?.use { it.readBytes() }
        ?: return
    val versionFile = externalAssetsDir.resolve("$name$VERSION_SUFFIX")
    val installedVersion = versionFile.takeIf { it.isFile }?.readBytes()
    if (installedVersion != null && compareUnsigned(bundledVersion, installedVersion) <= 0) return

    val archivePath = "$BUNDLED_RULE_SET_BASE/$name$ARCHIVE_SUFFIX"
    val archive = openBundledResource(archivePath) ?: run {
        Logs.w("bundled rule set $name has a version but no archive at $archivePath")
        return
    }
    geoDir.listFiles { file -> file.name.startsWith("$name-") }?.forEach { it.delete() }
    archive.use { unpackArchive(it, geoDir) }
    versionFile.writeBytes(bundledVersion)
    Logs.i("installed bundled rule set $name")
}

private fun compareUnsigned(left: ByteArray, right: ByteArray): Int {
    for (index in 0 until minOf(left.size, right.size)) {
        val difference = (left[index].toInt() and 0xff) - (right[index].toInt() and 0xff)
        if (difference != 0) return difference
    }
    return left.size - right.size
}
