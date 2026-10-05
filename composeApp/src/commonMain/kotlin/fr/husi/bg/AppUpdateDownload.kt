package fr.husi.bg

import fr.husi.ktx.USER_AGENT
import fr.husi.ktx.sha256Hex
import fr.husi.net.HttpFetchRequest
import fr.husi.net.HttpFetcher
import fr.husi.net.localSocks5Proxy
import fr.husi.net.resolveHttpFetcher
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
    httpFetcher: HttpFetcher = resolveHttpFetcher(),
    updateProgress: UpdateProgress = {},
): File = withContext(Dispatchers.IO) {
    val downloadUrl = requireNotNull(info.downloadUrl) { "no installable asset for this release" }
    val assetName = requireNotNull(info.assetName) { "no installable asset for this release" }

    val targetDir = appUpdateCacheDir(cacheDir).also { it.mkdirs() }
    targetDir.listFiles()?.forEach { it.delete() }
    val targetFile = targetDir.resolve(assetName)

    val request = HttpFetchRequest(
        url = downloadUrl,
        userAgent = USER_AGENT,
        noOverallDeadline = true,
        socks5 = localSocks5Proxy(),
    )
    httpFetcher.download(request, targetFile) { copiedBytes, contentLength ->
        // The release metadata knows the size even when the server omits it.
        val totalBytes = if (info.sizeBytes > 0) info.sizeBytes else contentLength
        if (totalBytes > 0) {
            updateProgress(copiedBytes * 100f / totalBytes)
        }
    }

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
