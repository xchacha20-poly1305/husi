package fr.husi.net

import fr.husi.ktx.asOkioSink
import fr.husi.ktx.atomicReplace
import fr.husi.ktx.createPlatformTempFile
import fr.husi.ktx.deleteBlocking
import fr.husi.ktx.toAbsoluteFile
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.parent
import io.github.xchacha20_poly1305.yan.Age
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import okio.BufferedSource
import okio.Sink
import okio.buffer
import okio.source
import java.nio.charset.StandardCharsets.UTF_8

internal class OkHttpFetcher(
    private val http: AppHttpClient,
) : HttpFetcher {
    override suspend fun fetchText(request: HttpFetchRequest): HttpTextResponse =
        fetch(request) { response ->
            val headers = response.headers
            val content = if (request.ageIdentities.isNullOrEmpty()) {
                response.body.bytes().decodeToString()
            } else {
                decrypt(response.body.source(), request.ageIdentities) { plaintext ->
                    plaintext.readByteArray().decodeToString()
                }
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
            fetch(request) { response ->
                partial.asOkioSink().buffer().use { output ->
                    val source = response.body.source()
                    if (request.ageIdentities.isNullOrEmpty()) {
                        copy(source, output, response.body.contentLength(), onProgress)
                    } else {
                        decrypt(source, request.ageIdentities) { plaintext ->
                            copy(plaintext, output, -1, onProgress)
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
        consume: suspend (Response) -> T,
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
        consume: suspend (Response) -> T,
    ): T {
        return http.exchange(
            request = httpRequest,
            proxy = fetchRequest.proxy,
            restrictedTls = fetchRequest.restrictedTls,
            pinnedSha256 = fetchRequest.pinnedSha256,
        ) { response, _ ->
            if (response.code != HttpStatus.OK) {
                throw response.statusError()
            }
            consume(response)
        }
    }

    private inline fun <T> decrypt(
        source: BufferedSource,
        identities: String,
        consume: (BufferedSource) -> T,
    ): T = withAgeIdentities(identities) { parsed ->
        Age.decrypt(source.inputStream(), parsed, armor = true).source().buffer().use(consume)
    }

    private suspend fun copy(source: BufferedSource, target: Sink, length: Long, onProgress: DownloadProgress) {
        val context = currentCoroutineContext()
        val sourceBuffer = Buffer()
        var copied = 0L
        while (true) {
            context.ensureActive()
            val count = source.read(sourceBuffer, COPY_CHUNK_SIZE)
            if (count == -1L) break
            target.write(sourceBuffer, count)
            copied += count
            onProgress(copied, length)
        }
    }

    private companion object {
        const val COPY_CHUNK_SIZE = 16 * 1024L
    }
}
