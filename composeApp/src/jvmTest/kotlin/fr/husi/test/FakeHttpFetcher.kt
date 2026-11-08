package fr.husi.test

import fr.husi.net.DownloadProgress
import fr.husi.net.HttpFetchRequest
import fr.husi.net.HttpFetcher
import fr.husi.net.HttpTextResponse
import java.io.File

/**
 * In-memory replacement for [HttpFetcher] used by tests.
 *
 * Every request is recorded in [requests], the most recent as [lastRequest].
 * Test bodies stage the next answer through the `next*` properties before
 * triggering the code under test.
 */
class FakeHttpFetcher : HttpFetcher {

    val requests = mutableListOf<HttpFetchRequest>()
    val lastRequest: HttpFetchRequest? get() = requests.lastOrNull()

    /** When non-null, the next request throws this instead of answering. */
    var nextThrowable: Throwable? = null

    /** Body [fetchText] answers with. */
    var nextResponseContent: ByteArray = ByteArray(0)

    /** Headers [fetchText] answers with; looked up case-insensitively. */
    val nextResponseHeaders = mutableMapOf<String, String>()

    /** Content length a [download] reports to its progress callback. */
    var nextDownloadBytes: Long = 1024L * 1024L

    /**
     * Bytes the next [download] actually writes to disk. Leave it null to write
     * exactly [nextDownloadBytes]; set it to model a truncated download.
     */
    var nextWrittenBytes: Long? = null

    /** Number of progress callbacks a [download] drives. */
    var nextChunkCount: Int = 4

    /** Where each [download] wrote, in order. */
    val downloadTargets = mutableListOf<File>()

    override suspend fun fetchText(request: HttpFetchRequest): HttpTextResponse {
        record(request)
        val headers = nextResponseHeaders.toMap()
        return HttpTextResponse(nextResponseContent.decodeToString()) { name ->
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        }
    }

    override suspend fun download(
        request: HttpFetchRequest,
        target: File,
        onProgress: DownloadProgress,
    ) {
        record(request)
        downloadTargets += target
        val written = nextWrittenBytes ?: nextDownloadBytes
        target.writeBytes(ByteArray(written.toInt()))

        val chunks = nextChunkCount.coerceAtLeast(1)
        val perChunk = written / chunks
        var copied = 0L
        repeat(chunks) { index ->
            copied += if (index == chunks - 1) written - copied else perChunk
            onProgress(copied, nextDownloadBytes)
        }
    }

    private fun record(request: HttpFetchRequest) {
        requests += request
        nextThrowable?.let { throw it }
    }
}
