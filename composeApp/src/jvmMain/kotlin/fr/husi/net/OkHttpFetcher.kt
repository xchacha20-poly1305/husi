package fr.husi.net

import fr.husi.ktx.asOkioSink
import fr.husi.ktx.atomicReplace
import fr.husi.ktx.createPlatformTempFile
import fr.husi.ktx.deleteBlocking
import fr.husi.ktx.toAbsoluteFile
import fr.husi.proto.v1.DecryptAgeRequest
import fr.husi.proto.v1.DecryptAgeResponse
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.parent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import okio.Sink
import okio.buffer
import java.nio.charset.StandardCharsets.UTF_8

internal class OkHttpFetcher(
    private val http: AppHttpClient,
    private val decryptAge: (Flow<DecryptAgeRequest>) -> Flow<DecryptAgeResponse>,
) : HttpFetcher {
    override suspend fun fetchText(request: HttpFetchRequest): HttpTextResponse =
        fetch(request) { response, cancel ->
            val headers = response.headers
            val content = if (request.ageIdentities.isNullOrEmpty()) {
                response.body.bytes().decodeToString()
            } else {
                val plaintext = Buffer()
                decrypt(response.body.source(), request.ageIdentities, cancel) { message ->
                    plaintext.write(message.chunk)
                }
                plaintext.readByteArray().decodeToString()
            }
            HttpTextResponse(content) {
                headers.values(it).firstOrNull()
            }
        }

    override suspend fun download(
        request: HttpFetchRequest,
        target: PlatformFile,
        onProgress: DownloadProgress,
    ) = withContext(Dispatchers.IO) {
        val destination = target.toAbsoluteFile()
        val partial = createPlatformTempFile("husi-http-", ".part", destination.parent())
        try {
            fetch(request) { response, cancel ->
                partial.asOkioSink().buffer().use { output ->
                    val source = response.body.source()
                    if (request.ageIdentities.isNullOrEmpty()) {
                        copy(source, output, response.body.contentLength(), onProgress)
                    } else {
                        var copied = 0L
                        decrypt(source, request.ageIdentities, cancel) { message ->
                            output.write(message.chunk)
                            copied += message.chunk.size
                            onProgress(copied, -1)
                        }
                    }
                }
            }
            ensureActive()
            partial.atomicReplace(destination)
        } finally {
            partial.deleteBlocking()
        }
    }

    private suspend fun <T : Any> fetch(
        request: HttpFetchRequest,
        consume: suspend (Response, () -> Unit) -> T,
    ): T {
        val url = request.url.toHttpUrl()
        val call = Request.Builder().url(url.withoutCredentials())
        for ((name, value) in request.headers) {
            call.header(name, value)
        }
        call.header("User-Agent", request.userAgent)
        if (url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty()) {
            call.header("Authorization", Credentials.basic(url.username, url.password, UTF_8))
        }
        val httpRequest = call.build()
        return if (request.noOverallDeadline) {
            executeFetch(httpRequest, request, consume)
        } else {
            withHttpDeadline(HTTP_TIMEOUT) {
                executeFetch(httpRequest, request, consume)
            }
        }
    }

    private suspend fun <T : Any> executeFetch(
        httpRequest: Request,
        fetchRequest: HttpFetchRequest,
        consume: suspend (Response, () -> Unit) -> T,
    ): T {
        return http.exchange(
            request = httpRequest,
            proxy = fetchRequest.proxy,
            restrictedTls = fetchRequest.restrictedTls,
            pinnedSha256 = fetchRequest.pinnedSha256,
        ) { response, cancel ->
            if (response.code != HttpStatus.OK) {
                throw response.statusError()
            }
            consume(response, cancel)
        }
    }

    private suspend fun decrypt(
        source: BufferedSource,
        identities: String,
        cancelHttp: () -> Unit,
        consume: (DecryptAgeResponse) -> Unit,
    ) = coroutineScope {
        val chunks = Channel<DecryptAgeRequest>(1)
        val reader = launch(Dispatchers.IO) {
            try {
                chunks.send(DecryptAgeRequest(identities = identities))
                while (!source.exhausted()) {
                    val available = minOf(source.buffer.size, AGE_CHUNK_SIZE)
                    chunks.send(DecryptAgeRequest(chunk = source.readByteString(available)))
                }
            } finally {
                chunks.close()
            }
        }
        try {
            decryptAge(chunks.receiveAsFlow()).collect(consume)
        } finally {
            // The decoder can reject a key or header before HTTP delivers the rest of the body.
            if (!reader.isCompleted) {
                reader.cancel()
                cancelHttp()
            }
        }
    }

    private fun copy(source: BufferedSource, target: Sink, length: Long, onProgress: DownloadProgress) {
        val sourceBuffer = Buffer()
        var copied = 0L
        while (true) {
            val count = source.read(sourceBuffer, AGE_CHUNK_SIZE)
            if (count == -1L) break
            target.write(sourceBuffer, count)
            copied += count
            onProgress(copied, length)
        }
    }

    private companion object {
        const val AGE_CHUNK_SIZE = 16 * 1024L
    }
}
