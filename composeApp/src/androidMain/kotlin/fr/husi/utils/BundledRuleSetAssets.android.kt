package fr.husi.utils

import android.content.res.AssetManager
import fr.husi.ktx.Logs
import fr.husi.repository.androidRepo
import java.io.File

internal actual suspend fun copyBundledRuleSetAssetsIfNeeded() {
    val context = androidRepo.context
    val assetManager = context.assets
    val targetDir = File(context.filesDir, "sing-box")
    syncBundledRuleSetAssets(
        targetDir = targetDir,
        readResourceBytes = { readAssetBytes(assetManager, it) },
        copyResource = { path, file -> copyAsset(assetManager, path, file) },
    )
}

private fun readAssetBytes(assetManager: AssetManager, path: String): ByteArray? {
    return try {
        assetManager.open(path).use { it.readBytes() }
    } catch (e: Exception) {
        Logs.e("Failed to read bundled asset $path", e)
        null
    }
}

private fun copyAsset(assetManager: AssetManager, assetPath: String, targetFile: File): Boolean {
    return try {
        targetFile.parentFile?.mkdirs()
        assetManager.open(assetPath).use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        true
    } catch (e: Exception) {
        Logs.e("Failed to copy bundled asset $assetPath", e)
        false
    }
}
