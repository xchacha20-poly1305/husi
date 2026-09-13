package fr.husi.bg

import fr.husi.database.DataStore
import fr.husi.ktx.USER_AGENT
import fr.husi.ktx.sha256Hex
import fr.husi.libcore.CopyCallback
import fr.husi.libcore.HttpClientFactory
import fr.husi.libcore.resolveHttpClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val UPDATE_CACHE_DIR_NAME = "update"

internal fun appUpdateCacheDir(cacheDir: File): File = cacheDir.resolve(UPDATE_CACHE_DIR_NAME)

class AppUpdateSizeMismatch(expected: Long, actual: Long) :
    IllegalStateException("the downloaded package is $actual bytes, expected $expected")

class AppUpdateChecksumMismatch(expected: String, actual: String) :
    IllegalStateException("the downloaded package hashes to $actual, expected $expected")

suspend fun downloadAppUpdate(
    info: AppUpdateInfo,
    cacheDir: File,
    httpClientFactory: HttpClientFactory = resolveHttpClientFactory(),
    updateProgress: UpdateProgress = {},
): File = withContext(Dispatchers.IO) {
    val downloadUrl = requireNotNull(info.downloadUrl) { "no installable asset for this release" }
    val assetName = requireNotNull(info.assetName) { "no installable asset for this release" }

    val targetDir = appUpdateCacheDir(cacheDir).also { it.mkdirs() }
    targetDir.listFiles()?.forEach { it.delete() }
    val targetFile = targetDir.resolve(assetName)

    httpClientFactory.newHttpClient().apply {
        keepAlive()
        if (DataStore.serviceState.connected) {
            useSocks5(
                DataStore.mixedPort.get(),
                DataStore.inboundUsername.get(),
                DataStore.inboundPassword.get(),
            )
        }
    }.newRequest().apply {
        setURL(downloadUrl)
        setUserAgent(USER_AGENT)
    }.execute().writeTo(
        targetFile.absolutePath,
        object : CopyCallback {
            private var saved = 0.0
            private var length = info.sizeBytes.toDouble()

            override fun setLength(length: Long) {
                if (this.length <= 0) this.length = length.toDouble()
            }

            override fun update(n: Long) {
                if (length <= 0) return
                saved += n.toDouble()
                updateProgress(((saved / length) * 100.0).toFloat())
            }
        },
    )

    val downloadedSize = targetFile.length()
    val expectedSize = info.sizeBytes
    if (downloadedSize == 0L || (expectedSize > 0L && downloadedSize != expectedSize)) {
        targetFile.delete()
        throw AppUpdateSizeMismatch(expectedSize, downloadedSize)
    }

    info.sha256?.let { expectedHash ->
        val downloadedHash = targetFile.sha256Hex()
        if (!downloadedHash.equals(expectedHash, ignoreCase = true)) {
            targetFile.delete()
            throw AppUpdateChecksumMismatch(expectedHash, downloadedHash)
        }
    }

    targetFile
}
