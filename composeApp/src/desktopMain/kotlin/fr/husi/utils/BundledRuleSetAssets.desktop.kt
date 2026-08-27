package fr.husi.utils

import fr.husi.ktx.Logs
import fr.husi.repository.resolveRepository
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.parent
import io.github.vinceglb.filekit.write

internal actual suspend fun copyBundledRuleSetAssetsIfNeeded() {
    val classLoader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    val targetDir = resolveRepository().filesDir / "sing-box"
    syncBundledRuleSetAssets(
        targetDir = targetDir,
        readResourceBytes = { readResourceBytes(classLoader, it) },
        copyResource = { path, file -> copyResource(classLoader, path, file) },
    )
}

private fun readResourceBytes(classLoader: ClassLoader, path: String): ByteArray? {
    return try {
        classLoader.getResourceAsStream(path)?.use { it.readBytes() }
    } catch (e: Exception) {
        Logs.e("read bundled asset $path", e)
        null
    }
}

private suspend fun copyResource(classLoader: ClassLoader, resourcePath: String, targetFile: PlatformFile): Boolean {
    return try {
        targetFile.parent()?.createDirectories()
        val bytes = classLoader.getResourceAsStream(resourcePath)?.use { it.readBytes() } ?: return false
        targetFile.write(bytes)
        true
    } catch (e: Exception) {
        Logs.e("copy bundled asset $resourcePath", e)
        false
    }
}
