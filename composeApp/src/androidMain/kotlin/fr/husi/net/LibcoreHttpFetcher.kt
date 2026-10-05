package fr.husi.net

import fr.husi.libcore.CopyCallback
import fr.husi.libcore.HTTPResponse
import fr.husi.libcore.Libcore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** [HttpFetcher] over the in-process libcore binding. */
object LibcoreHttpFetcher : HttpFetcher {

    override suspend fun fetchText(request: HttpFetchRequest): HttpTextResponse {
        return execute(request).closeOnCancel { response ->
            val content = response.contentString
            HttpTextResponse(content) { name -> response.getHeader(name).ifEmpty { null } }
        }
    }

    override suspend fun download(
        request: HttpFetchRequest,
        target: File,
        onProgress: DownloadProgress,
    ) {
        execute(request).closeOnCancel { response ->
            response.writeTo(
                target.absolutePath,
                object : CopyCallback {
                    private var contentLength = -1L
                    private var copiedBytes = 0L

                    override fun setLength(length: Long) {
                        contentLength = length
                    }

                    override fun update(n: Long) {
                        copiedBytes += n
                        onProgress(copiedBytes, contentLength)
                    }
                },
            )
        }
    }

    private suspend fun execute(request: HttpFetchRequest): HTTPResponse =
        withContext(Dispatchers.IO) {
            val client = Libcore.newHttpClient().also { client ->
                if (request.restrictedTls) client.restrictedTLS()
                request.pinnedSha256?.let { client.pinnedSHA256(it) }
                request.socks5?.let { client.useSocks5(it.port, it.username, it.password) }
                request.ageIdentities?.let { client.setAgeKey(it) }
            }
            client.newRequest().also { httpRequest ->
                httpRequest.setURL(request.url)
                httpRequest.setUserAgent(request.userAgent)
                for ((key, value) in request.headers) {
                    httpRequest.setHeader(key, value)
                }
                if (request.noOverallDeadline) httpRequest.setTimeout(NO_OVERALL_DEADLINE_MS)
            }.execute()
        }

    /**
     * Reads the body on the IO dispatcher. Go cannot be interrupted, so a
     * cancelled caller closes the response instead, which fails the read.
     */
    private suspend fun <T> HTTPResponse.closeOnCancel(block: (HTTPResponse) -> T): T =
        coroutineScope {
            val closer = launch {
                try {
                    awaitCancellation()
                } finally {
                    runCatching { close() }
                }
            }
            try {
                withContext(Dispatchers.IO) { block(this@closeOnCancel) }
            } finally {
                closer.cancel()
            }
        }

    /** [fr.husi.libcore.HTTPRequest.setTimeout] treats zero as no overall deadline. */
    private const val NO_OVERALL_DEADLINE_MS = 0
}
