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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Sink
import okio.buffer
import okio.source
import retrofit2.Response

internal class RetrofitHttpFetcher(
    private val api: RawHttpApi,
) : HttpFetcher {
    override suspend fun fetchText(request: HttpFetchRequest): HttpTextResponse =
        fetch(request) { response, body ->
            val headers = response.headers()
            val content = if (request.ageIdentities.isNullOrEmpty()) {
                body.bytes().decodeToString()
            } else {
                decrypt(body.source(), request.ageIdentities) { plaintext ->
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
            fetch(request) { _, body ->
                partial.asOkioSink().buffer().use { output ->
                    val source = body.source()
                    if (request.ageIdentities.isNullOrEmpty()) {
                        copy(source, output, body.contentLength(), onProgress)
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
        consume: suspend (Response<ResponseBody>, ResponseBody) -> T,
    ): T {
        val url = request.url.toHttpUrl()
        val headers = buildMap {
            putAll(request.headers)
            put("User-Agent", request.userAgent)
            url.basicAuthorization()?.let { put("Authorization", it) }
        }
        val targetUrl = url.withoutCredentials()
        val options = HttpRequestOptions(
            proxy = request.proxy,
            restrictedTls = request.restrictedTls,
            pinnedSha256 = request.pinnedSha256,
        )
        return if (request.noOverallDeadline) {
            executeFetch(targetUrl, headers, options, consume)
        } else {
            withHttpDeadline(HTTP_TIMEOUT) {
                executeFetch(targetUrl, headers, options, consume)
            }
        }
    }

    private suspend fun <T : Any> executeFetch(
        url: HttpUrl,
        headers: Map<String, String>,
        options: HttpRequestOptions,
        consume: suspend (Response<ResponseBody>, ResponseBody) -> T,
    ): T {
        return api.fetch(url, headers, options).awaitAndUse { response ->
            val body = response.requireOkBody()
            consume(response, body)
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
