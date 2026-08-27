package fr.husi.utils

import fr.husi.ktx.Logs
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.readBytes
import io.github.vinceglb.filekit.write

private const val BUNDLED_RULE_SET_BASE = "composeResources/fr.husi.resources/files/sing-box"
private val RULE_SET_NAMES = listOf("geoip", "geosite")

internal expect suspend fun copyBundledRuleSetAssetsIfNeeded()

internal suspend fun syncBundledRuleSetAssets(
    targetDir: PlatformFile,
    readResourceBytes: suspend (String) -> ByteArray?,
    copyResource: suspend (String, PlatformFile) -> Boolean,
) {
    targetDir.createDirectories()

    for (name in RULE_SET_NAMES) {
        val versionPath = "$BUNDLED_RULE_SET_BASE/$name.version.txt"
        val archivePath = "$BUNDLED_RULE_SET_BASE/$name.tar.zst"

        val versionBytes = readResourceBytes(versionPath) ?: continue
        val versionFile = targetDir / "$name.version.txt"
        val archiveFile = targetDir / "$name.tar.zst"

        val existingVersion = if (versionFile.isRegularFile()) {
            runCatching { versionFile.readBytes() }.getOrNull()
        } else {
            null
        }
        val shouldCopy = existingVersion == null ||
            !archiveFile.isRegularFile() ||
            !existingVersion.contentEquals(versionBytes)
        if (!shouldCopy) continue

        if (!copyResource(archivePath, archiveFile)) continue

        try {
            versionFile.write(versionBytes)
        } catch (e: Exception) {
            Logs.e("Failed to write bundled asset version $name", e)
        }
    }
}
