package fr.husi.utils

import android.content.res.AssetManager
import fr.husi.ktx.Logs
import fr.husi.repository.resolveAndroidRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.sink
import kotlinx.io.Buffer
import kotlinx.io.asSource

internal actual suspend fun copyBundledRuleSetAssetsIfNeeded() {
    val context = resolveAndroidRepository().context
    val assetManager = context.assets
    val targetDir = PlatformFile(context.filesDir) / "sing-box"
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

private fun copyAsset(assetManager: AssetManager, assetPath: String, targetFile: PlatformFile): Boolean {
    return try {
        targetFile.parent()?.createDirectories()
        assetManager.open(assetPath).use { input ->
            input.asSource().use { source ->
                targetFile.sink().use { sink ->
                    val buffer = Buffer()
                    while (true) {
                        val bytesRead = source.readAtMostTo(buffer, COPY_BUFFER_SIZE_BYTES)
                        if (bytesRead == -1L) break
                        sink.write(buffer, bytesRead)
                    }
                    sink.flush()
                }
            }
        }
        true
    } catch (e: Exception) {
        Logs.e("Failed to copy bundled asset $assetPath", e)
        false
    }
}

private const val COPY_BUFFER_SIZE_BYTES = 8192L
