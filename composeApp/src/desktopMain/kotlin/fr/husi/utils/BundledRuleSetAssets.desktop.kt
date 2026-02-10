package fr.husi.utils

import fr.husi.ktx.Logs
import fr.husi.repository.repo
import java.io.File

internal actual suspend fun copyBundledRuleSetAssetsIfNeeded() {
    val classLoader = Thread.currentThread().contextClassLoader ?: ClassLoader.getSystemClassLoader()
    val targetDir = File(repo.filesDir, "sing-box")
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

private fun copyResource(classLoader: ClassLoader, resourcePath: String, targetFile: File): Boolean {
    return try {
        targetFile.parentFile?.mkdirs()
        classLoader.getResourceAsStream(resourcePath)?.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: return false
        true
    } catch (e: Exception) {
        Logs.e("copy bundled asset $resourcePath", e)
        false
    }
}
