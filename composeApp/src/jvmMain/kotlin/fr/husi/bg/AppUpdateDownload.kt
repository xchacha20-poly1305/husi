package fr.husi.bg

import androidx.compose.ui.util.fastCoerceAtLeast
import fr.husi.ktx.deleteBlocking
import fr.husi.ktx.listFilesOrNull
import fr.husi.ktx.sha256Hex
import fr.husi.net.HttpFetchRequest
import fr.husi.net.HttpFetcher
import fr.husi.net.buildUserAgent
import fr.husi.net.localHttpProxy
import fr.husi.net.resolveHttpFetcher
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.resolve
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val UPDATE_CACHE_DIR_NAME = "update"

internal fun appUpdateCacheDir(cacheDir: PlatformFile): PlatformFile = cacheDir.resolve(UPDATE_CACHE_DIR_NAME)

class AppUpdateSizeMismatch(expected: Long, actual: Long) :
    IllegalStateException("the downloaded package is $actual bytes, expected $expected")

class AppUpdateChecksumMismatch(expected: String, actual: String) :
    IllegalStateException("the downloaded package hashes to $actual, expected $expected")

suspend fun downloadAppUpdate(
    info: AppUpdateInfo,
    cacheDir: PlatformFile,
    httpFetcher: HttpFetcher = resolveHttpFetcher(),
    updateProgress: UpdateProgress = {},
): PlatformFile = withContext(Dispatchers.IO) {
    val downloadUrl = requireNotNull(info.downloadUrl) { "no installable asset for this release" }
    val assetName = requireNotNull(info.assetName) { "no installable asset for this release" }

    val targetDir = appUpdateCacheDir(cacheDir).also {
        it.createDirectories()
    }
    targetDir.listFilesOrNull()?.forEach {
        it.deleteBlocking()
    }
    val targetFile = targetDir.resolve(assetName)

    val request = HttpFetchRequest(
        url = downloadUrl,
        userAgent = buildUserAgent(),
        noOverallDeadline = true,
        proxy = localHttpProxy(),
    )
    httpFetcher.download(request, targetFile) { copiedBytes, contentLength ->
        // The release metadata knows the size even when the server omits it.
        val totalBytes = if (info.sizeBytes > 0) info.sizeBytes else contentLength
        if (totalBytes > 0) {
            updateProgress(copiedBytes * 100f / totalBytes)
        }
    }

    val downloadedSize = targetFile.size().fastCoerceAtLeast(0)
    val expectedSize = info.sizeBytes
    if (downloadedSize == 0L || (expectedSize > 0L && downloadedSize != expectedSize)) {
        targetFile.deleteBlocking()
        throw AppUpdateSizeMismatch(expectedSize, downloadedSize)
    }

    info.sha256?.let { expectedHash ->
        val downloadedHash = targetFile.sha256Hex()
        if (!downloadedHash.equals(expectedHash, ignoreCase = true)) {
            targetFile.deleteBlocking()
            throw AppUpdateChecksumMismatch(expectedHash, downloadedHash)
        }
    }

    targetFile
}
