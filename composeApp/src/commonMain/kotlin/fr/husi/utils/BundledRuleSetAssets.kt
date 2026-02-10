package fr.husi.utils

import fr.husi.ktx.Logs
import java.io.File

private const val BUNDLED_RULE_SET_BASE = "composeResources/fr.husi.resources/files/sing-box"
private val RULE_SET_NAMES = listOf("geoip", "geosite")

internal expect suspend fun copyBundledRuleSetAssetsIfNeeded()

internal suspend fun syncBundledRuleSetAssets(
    targetDir: File,
    readResourceBytes: suspend (String) -> ByteArray?,
    copyResource: suspend (String, File) -> Boolean,
) {
    targetDir.mkdirs()

    for (name in RULE_SET_NAMES) {
        val versionPath = "$BUNDLED_RULE_SET_BASE/$name.version.txt"
        val archivePath = "$BUNDLED_RULE_SET_BASE/$name.tar.zst"

        val versionBytes = readResourceBytes(versionPath) ?: continue
        val versionFile = File(targetDir, "$name.version.txt")
        val archiveFile = File(targetDir, "$name.tar.zst")

        val existingVersion = if (versionFile.isFile) {
            runCatching { versionFile.readBytes() }.getOrNull()
        } else {
            null
        }
        val shouldCopy = existingVersion == null ||
            !archiveFile.isFile ||
            !existingVersion.contentEquals(versionBytes)
        if (!shouldCopy) continue

        if (!copyResource(archivePath, archiveFile)) continue

        try {
            versionFile.writeBytes(versionBytes)
        } catch (e: Exception) {
            Logs.e("Failed to write bundled asset version $name", e)
        }
    }
}
