package fr.husi.net

import fr.husi.core.CoreClient
import fr.husi.proto.v1.HTTPFetchHead
import fr.husi.proto.v1.HTTPFetchResponse
import fr.husi.proto.v1.hTTPFetchRequest
import fr.husi.proto.v1.hTTPFetchSocks5
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream
import java.util.TreeMap

class GrpcHttpFetcher(private val coreClient: CoreClient) : HttpFetcher {

    override suspend fun fetchText(request: HttpFetchRequest): HttpTextResponse {
        val body = ByteArrayOutputStream()
        val head = fetch(request, body) { _, _ -> }
        val headers = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply {
            putAll(head.headersMap)
        }
        return HttpTextResponse(body.toString(Charsets.UTF_8)) { name -> headers[name] }
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
            when (message.payloadCase) {
                HTTPFetchResponse.PayloadCase.HEAD -> head = message.head

                HTTPFetchResponse.PayloadCase.CHUNK -> {
                    val receivedHead = checkNotNull(head) { "HTTPFetch sent a chunk before its head" }
                    withContext(Dispatchers.IO) { message.chunk.writeTo(output) }
                    copiedBytes += message.chunk.size()
                    onProgress(copiedBytes, receivedHead.contentLength)
                }

                else -> Unit
            }
        }
        return checkNotNull(head) { "HTTPFetch ended without a response" }
    }

    private fun protoRequest(request: HttpFetchRequest) = hTTPFetchRequest {
        url = request.url
        headers.putAll(request.headers)
        headers[USER_AGENT_HEADER] = request.userAgent
        noOverallDeadline = request.noOverallDeadline
        restrictedTls = request.restrictedTls
        request.pinnedSha256?.let { pinnedSha256 = it }
        request.socks5?.let { proxy ->
            socks5 = hTTPFetchSocks5 {
                port = proxy.port
                username = proxy.username
                password = proxy.password
            }
        }
        request.ageIdentities?.let { ageIdentities = it }
    }

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent"
    }
}
