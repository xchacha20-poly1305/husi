package fr.husi.tailscale

import fr.husi.core.CoreClient
import fr.husi.ktx.asOkioSink
import fr.husi.ktx.atomicReplace
import fr.husi.ktx.createPlatformTempFile
import fr.husi.ktx.deleteBlocking
import fr.husi.proto.daemon.TaildropFile
import fr.husi.proto.daemon.TaildropReceivingFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.createDirectories
import io.github.vinceglb.filekit.isRegularFile
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okio.buffer
import java.net.URLConnection

const val TAILDROP_DIRECTORY_NAME = "Taildrop"

const val TAILDROP_DOWNLOAD_DIRECTORY_NAME = "TaildropDownloads"

const val TAILDROP_PARTIAL_SUFFIX = ".partial"

fun TaildropFile.toState() = TaildropFileState(
    name = name,
    size = size,
    senderName = senderName,
    modifiedAt = modifiedAt,
)

fun TaildropReceivingFile.toState() = TaildropReceivingFileState(
    name = name,
    size = size,
    receivedBytes = receivedBytes,
    senderId = senderID,
    senderName = senderName,
)

internal fun isTaildropPathSegment(name: String): Boolean {
    if (name.isEmpty() || name == "." || name == "..") return false
    if (name.contains('/') || name.contains('\\') || name.contains('\u0000')) {
        return false
    }
    return true
}

fun taildropMimeType(name: String): String {
    return URLConnection.guessContentTypeFromName(name) ?: "application/octet-stream"
}

suspend fun materializeTaildropFile(
    coreClient: CoreClient,
    receiveDirectory: PlatformFile?,
    downloadDirectory: PlatformFile,
    endpointTag: String,
    name: String,
): PlatformFile {
    if (!isTaildropPathSegment(name)) error("invalid taildrop file name")
    receiveDirectory?.resolve(name)?.takeIf { it.isRegularFile() }?.let { return it }
    downloadDirectory.createDirectories()
    val destination = downloadDirectory.resolve(name)
    val partial = createPlatformTempFile("taildrop-", TAILDROP_PARTIAL_SUFFIX, downloadDirectory)
    try {
        partial.asOkioSink().buffer().use { sink ->
            coreClient.downloadTaildropFile(endpointTag, name).collect { chunk ->
                sink.write(chunk.data_)
            }
        }
        currentCoroutineContext().ensureActive()
        partial.atomicReplace(destination)
    } finally {
        partial.deleteBlocking()
    }
    return destination
}
