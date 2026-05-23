package fr.husi.test

import fr.husi.libcore.CopyCallback
import fr.husi.libcore.HTTPClient
import fr.husi.libcore.HTTPRequest
import fr.husi.libcore.HTTPResponse
import fr.husi.libcore.HttpClientFactory
import fr.husi.libcore.URL

/**
 * In-memory replacement for [HttpClientFactory] used by tests.
 *
 * Each [newHttpClient] call returns a fresh [FakeHTTPClient]; the most recent one
 * is exposed as [lastClient] for assertions. Test bodies can stage a download or
 * upload by setting [nextDownloadBytes] / [nextChunkCount], or force a failure
 * by setting [nextThrowable] before triggering the code under test.
 */
class FakeHttpClientFactory : HttpClientFactory {

    val clients = mutableListOf<FakeHTTPClient>()
    val lastClient: FakeHTTPClient? get() = clients.lastOrNull()

    /** Total bytes the next response will report through [CopyCallback.setLength]. */
    var nextDownloadBytes: Long = 1024L * 1024L

    /** Number of [CopyCallback.update] callbacks driven during writeTo / setContentZero. */
    var nextChunkCount: Int = 4

    /** When non-null, the next [HTTPRequest.execute] throws this instead of returning a response. */
    var nextThrowable: Throwable? = null

    override val userAgent: String = "husi-test/0"

    override fun newHttpClient(): HTTPClient =
        FakeHTTPClient(this).also { clients.add(it) }

    override fun parseURL(urlString: String): URL = FakeURL(urlString)
}

class FakeHTTPClient(private val factory: FakeHttpClientFactory) : HTTPClient {
    var socks5: Socks5Config? = null
        private set
    var closed: Int = 0
        private set
    val requests = mutableListOf<FakeHTTPRequest>()
    val lastRequest: FakeHTTPRequest? get() = requests.lastOrNull()

    override fun close() {
        closed++
    }

    override fun keepAlive() {}
    override fun pinnedSHA256(sha256: String?) {}
    override fun restrictedTLS() {}

    override fun useSocks5(port: Int, username: String?, password: String?) {
        socks5 = Socks5Config(port, username.orEmpty(), password.orEmpty())
    }

    override fun newRequest(): HTTPRequest =
        FakeHTTPRequest(factory).also { requests.add(it) }

    data class Socks5Config(val port: Int, val username: String, val password: String)
}

class FakeHTTPRequest(private val factory: FakeHttpClientFactory) : HTTPRequest {
    var url: String? = null
        private set
    var userAgent: String? = null
        private set
    var timeout: Int? = null
        private set
    val headers = mutableMapOf<String, String>()
    var method: String? = null
        private set
    var contentZero: ContentZero? = null
        private set

    override fun execute(): HTTPResponse {
        factory.nextThrowable?.let { throw it }
        contentZero?.let { it.callback.drive(it.length, factory.nextChunkCount) }
        return FakeHTTPResponse(factory).also { lastResponse = it }
    }

    var lastResponse: FakeHTTPResponse? = null
        private set

    override fun setURL(url: String) {
        this.url = url
    }

    override fun setUserAgent(userAgent: String) {
        this.userAgent = userAgent
    }

    override fun setTimeout(timeout: Int) {
        this.timeout = timeout
    }

    override fun setHeader(key: String, value: String) {
        headers[key] = value
    }

    override fun setMethod(method: String) {
        this.method = method
    }

    override fun setContent(body: ByteArray?) {}
    override fun setContentString(body: String?) {}

    override fun setContentZero(length: Long, callback: CopyCallback) {
        contentZero = ContentZero(length, callback)
    }

    data class ContentZero(val length: Long, val callback: CopyCallback)
}

class FakeHTTPResponse(private val factory: FakeHttpClientFactory) : HTTPResponse {
    var closed: Int = 0
        private set
    var writeToTarget: String? = null
        private set

    override fun close() {
        closed++
    }

    override fun getContentString(): String = ""
    override fun getHeader(key: String?): String = ""

    override fun writeTo(target: String, callback: CopyCallback) {
        writeToTarget = target
        callback.drive(factory.nextDownloadBytes, factory.nextChunkCount)
    }
}

/**
 * Minimal [URL] implementation backed by [java.net.URI] parsing. Only [getScheme]
 * and [getHost] are exercised by callers; the remaining mutators are not used
 * by the speed-test code path and throw to surface accidental dependence.
 */
class FakeURL(private val raw: String) : URL {
    private val uri = java.net.URI(raw)
    override fun getScheme(): String = uri.scheme ?: ""
    override fun getHost(): String = uri.host ?: ""

    override fun addQueryParameter(key: String?, value: String?) = unsupported()
    override fun getFragment(): String = uri.fragment ?: ""
    override fun getFullHost(): String = uri.host ?: ""
    override fun getOpaque(): String = ""
    override fun getPassword(): String = ""
    override fun getPath(): String = uri.path ?: ""
    override fun getPorts(): String = uri.port.takeIf { it >= 0 }?.toString() ?: ""
    override fun getRawFragment(): String = uri.rawFragment ?: ""
    override fun getRawPath(): String = uri.rawPath ?: ""
    override fun getString(): String = raw
    override fun getUsername(): String = ""
    override fun queryParameter(key: String?): String = ""
    override fun queryParameterUnescape(key: String?): String = ""
    override fun setFragment(value: String?) = unsupported()
    override fun setFullHost(value: String?) = unsupported()
    override fun setHost(value: String?) = unsupported()
    override fun setOpaque(value: String?) = unsupported()
    override fun setPassword(value: String?) = unsupported()
    override fun setPath(value: String?) = unsupported()
    override fun setPorts(value: String?) = unsupported()
    override fun setQueryParameter(key: String?, value: String?) = unsupported()
    override fun setRawFragment(value: String?) = unsupported()
    override fun setRawPath(value: String?) = unsupported()
    override fun setScheme(value: String?) = unsupported()
    override fun setUsername(value: String?) = unsupported()

    private fun unsupported(): Nothing =
        throw UnsupportedOperationException("FakeURL only implements scheme/host accessors")
}

private fun CopyCallback.drive(totalBytes: Long, chunkCount: Int) {
    setLength(totalBytes)
    val chunks = chunkCount.coerceAtLeast(1)
    val per = totalBytes / chunks
    val remainder = totalBytes - per * chunks
    repeat(chunks - 1) { update(per) }
    update(per + remainder)
}
