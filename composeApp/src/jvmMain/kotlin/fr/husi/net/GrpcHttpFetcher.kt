package fr.husi.net

import fr.husi.core.CoreClient
import fr.husi.proto.v1.HTTPFetchHead
import fr.husi.proto.v1.HTTPFetchRequest
import fr.husi.proto.v1.HTTPFetchSocks5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.*

class GrpcHttpFetcher(private val coreClient: CoreClient) : HttpFetcher {

    override suspend fun fetchText(request: HttpFetchRequest): HttpTextResponse {
        val body = ByteArrayOutputStream()
        val head = fetch(request, body) { _, _ -> }
        val headers = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply {
            putAll(head.headers)
        }
        return HttpTextResponse(body.toByteArray().decodeToString()) { name -> headers[name] }
    }

    override suspend fun download(
        request: HttpFetchRequest,
        target: File,
        onProgress: DownloadProgress,
    ) {
        withContext(Dispatchers.IO) {
            target.outputStream().use { output ->
                fetch(request, output, onProgress)
            }
        }
    }

    private suspend fun fetch(
        request: HttpFetchRequest,
        output: OutputStream,
        onProgress: DownloadProgress,
    ): HTTPFetchHead {
        var head: HTTPFetchHead? = null
        var copiedBytes = 0L
        coreClient.httpFetch(protoRequest(request)).collect { message ->
            when {
                message.head != null -> head = message.head

                message.chunk != null -> {
                    val receivedHead = checkNotNull(head) { "HTTPFetch sent a chunk before its head" }
                    withContext(Dispatchers.IO) {
                        checkNotNull(message.chunk).write(output)
                    }
                    copiedBytes += checkNotNull(message.chunk).size
                    onProgress(copiedBytes, receivedHead.content_length)
                }

                else -> Unit
            }
        }
        return checkNotNull(head) { "HTTPFetch ended without a response" }
    }

    private fun protoRequest(request: HttpFetchRequest) =
        HTTPFetchRequest(
            url = request.url,
            headers = request.headers + (USER_AGENT_HEADER to request.userAgent),
            no_overall_deadline = request.noOverallDeadline,
            restricted_tls = request.restrictedTls,
            pinned_sha256 = request.pinnedSha256.orEmpty(),
            socks5 = request.socks5?.let { proxy ->
                HTTPFetchSocks5(
                    port = proxy.port,
                    username = proxy.username,
                    password = proxy.password,
                )
            },
            age_identities = request.ageIdentities.orEmpty(),
        )

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent"
    }
}
