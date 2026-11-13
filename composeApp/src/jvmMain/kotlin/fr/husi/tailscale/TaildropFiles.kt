package fr.husi.tailscale

import fr.husi.core.CoreClient
import fr.husi.proto.daemon.TaildropFile
import fr.husi.proto.daemon.TaildropReceivingFile
import okio.buffer
import okio.sink
import java.io.File
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
    receiveDirectory: File?,
    downloadDirectory: File,
    endpointTag: String,
    name: String,
): File {
    if (!isTaildropPathSegment(name)) error("invalid taildrop file name")
    receiveDirectory?.resolve(name)?.takeIf { it.isFile }?.let { return it }
    downloadDirectory.mkdirs()
    val destination = downloadDirectory.resolve(name)
    val partial = downloadDirectory.resolve(name + TAILDROP_PARTIAL_SUFFIX)
    try {
        partial.sink().buffer().use { sink ->
            coreClient.downloadTaildropFile(endpointTag, name).collect { chunk ->
                sink.write(chunk.data_)
            }
        }
    } catch (e: Throwable) {
        partial.delete()
        throw e
    }
    if (destination.exists() && !destination.delete()) {
        partial.delete()
        error("failed to replace $name")
    }
    if (!partial.renameTo(destination)) {
        partial.copyTo(destination, overwrite = true)
        partial.delete()
    }
    return destination
}
