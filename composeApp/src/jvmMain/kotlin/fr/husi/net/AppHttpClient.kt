@file:OptIn(ExperimentalAtomicApi::class)

package fr.husi.net

import fr.husi.CertProvider
import fr.husi.core.rootCertificatesTrustManager
import fr.husi.ktx.readBytesBlocking
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.isRegularFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.EventListener
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.TlsVersion
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.toByteString
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets.UTF_8
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal val HTTP_TIMEOUT = 15_000.milliseconds

internal expect fun appTlsContext(trust: X509TrustManager, restrictedTls: Boolean): SSLContext

internal data class HttpRequestOptions(
    val proxy: LocalHttpProxy? = null,
    val restrictedTls: Boolean = false,
    val pinnedSha256: String? = null,
    val sameOriginRedirects: Boolean = false,
    val emptyBodyStatusCodes: Set<Int> = emptySet(),
) {
    companion object {
        val DEFAULT = HttpRequestOptions()
    }
}

internal class AppHttpClient(
    private val rootsFile: PlatformFile,
    private val prepareRoots: suspend () -> Unit = {},
    private val directCallFactory: suspend (isHttps: Boolean) -> Call.Factory? = { null },
) : Call.Factory {
    private val base = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(HTTP_TIMEOUT)
        .readTimeout(HTTP_TIMEOUT)
        .writeTimeout(HTTP_TIMEOUT)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private val enqueueScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private class Roots(val pem: ByteString) {
        val trust = rootCertificatesTrustManager(pem.toByteArray().inputStream())
        val socketFactory = appTlsContext(trust, restrictedTls = false).socketFactory
    }

    private var cachedRoots: Roots? = null

    override fun newCall(request: Request): Call = AppHttpCall(request)

    private suspend fun roots(): Roots {
        val hasRootsFile = withContext(Dispatchers.IO) { rootsFile.isRegularFile() }
        if (!hasRootsFile) {
            prepareRoots()
        }
        return withContext(Dispatchers.IO) {
            val pem = rootsFile.readBytesBlocking().toByteString()
            synchronized(this@AppHttpClient) {
                val existing = cachedRoots
                if (existing != null && existing.pem == pem) {
                    existing
                } else {
                    Roots(pem).also { cachedRoots = it }
                }
            }
        }
    }

    private suspend fun newInnerCall(
        request: Request,
        proxy: LocalHttpProxy?,
        restrictedTls: Boolean,
        pin: String?,
    ): Call {
        if (proxy == null && !restrictedTls && pin == null) {
            directCallFactory(request.url.isHttps)?.let { factory ->
                return factory.newCall(request)
            }
        }
        val builder = base.newBuilder()
        if (proxy != null) {
            builder.configureProxy(proxy)
        }

        if (request.url.isHttps) {
            val roots = roots()
            val trust = if (pin != null) {
                PinnedLeafTrust(roots.trust, pin)
            } else {
                roots.trust
            }
            val socketFactory = if (restrictedTls || pin != null) {
                appTlsContext(trust, restrictedTls).socketFactory
            } else {
                roots.socketFactory
            }
            builder.sslSocketFactory(socketFactory, trust)
            if (restrictedTls) {
                builder.connectionSpecs(listOf(TLS_13))
            }
            if (pin != null) {
                builder.hostnameVerifier { host, session ->
                    val leaf = session.peerCertificates.firstOrNull() as? X509Certificate
                    if (leaf != null && leaf.matchesPin(pin)) {
                        host == request.url.host
                    } else {
                        base.hostnameVerifier.verify(host, session)
                    }
                }
            }
        }
        return builder.build().newCall(request)
    }

    private fun OkHttpClient.Builder.configureProxy(proxy: LocalHttpProxy) {
        val address = Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", proxy.port))
        proxy(address)
        if (proxy.username.isBlank() && proxy.password.isBlank()) {
            return
        }
        val credentials = Credentials.basic(proxy.username, proxy.password, UTF_8)
        addNetworkInterceptor { chain ->
            val outgoing = chain.request()
            if (!outgoing.url.isHttps && chain.connection()?.route()?.proxy == address) {
                val authorized = outgoing.newBuilder()
                    .header(PROXY_AUTHORIZATION, credentials)
                    .build()
                chain.proceed(authorized)
            } else {
                chain.proceed(outgoing)
            }
        }
        proxyAuthenticator { route, response ->
            val outgoing = response.request
            val needsConnectAuth = route?.proxy == address &&
                outgoing.method == HttpMethod.CONNECT &&
                outgoing.header(PROXY_AUTHORIZATION) == null
            if (needsConnectAuth) {
                outgoing.newBuilder()
                    .header(PROXY_AUTHORIZATION, credentials)
                    .build()
            } else {
                null
            }
        }
    }

    private fun redirectRequest(
        current: Request,
        destination: HttpUrl,
        sameOriginRedirects: Boolean,
        pin: String?,
    ): Request {
        val sameOrigin = current.url.sameOrigin(destination)
        if (!sameOrigin && (sameOriginRedirects || pin != null)) {
            throw IOException("Cross-origin redirect")
        }
        val next = current.newBuilder().url(destination.withoutCredentials())
        if (!sameOrigin) {
            next.removeHeader("Authorization")
            next.removeHeader("Cookie")
        }
        return next.build()
    }

    private inner class AppHttpCall(
        private val initialRequest: Request,
    ) : Call {
        private val executed = AtomicBoolean(false)
        private val canceled = AtomicBoolean(false)
        private val activeCall = AtomicReference<Call?>(null)
        private val activeJob = AtomicReference<Job?>(null)
        private val tags = ConcurrentHashMap<Any, Any>()

        override fun request(): Request = initialRequest

        override fun execute(): Response {
            check(executed.compareAndSet(false, newValue = true)) { "Already Executed" }
            if (canceled.load()) {
                throw IOException("Canceled")
            }
            return runBlocking(Dispatchers.IO) {
                executeWithRedirects()
            }
        }

        override fun enqueue(responseCallback: Callback) {
            check(executed.compareAndSet(expectedValue = false, newValue = true)) { "Already Executed" }
            if (canceled.load()) {
                responseCallback.onFailure(this, IOException("Canceled"))
                return
            }
            val job = enqueueScope.launch {
                val result = try {
                    Result.success(executeWithRedirects())
                } catch (e: IOException) {
                    Result.failure(e)
                } catch (e: CancellationException) {
                    Result.failure(IOException("Canceled", e))
                } catch (e: Exception) {
                    Result.failure(IOException(e.message ?: e::class.simpleName, e))
                }
                result.fold(
                    onSuccess = { responseCallback.onResponse(this@AppHttpCall, it) },
                    onFailure = { responseCallback.onFailure(this@AppHttpCall, it as IOException) },
                )
            }
            activeJob.store(job)
            if (canceled.load()) {
                job.cancel()
            }
        }

        override fun cancel() {
            if (!canceled.compareAndSet(expectedValue = false, newValue = true)) return
            activeCall.load()?.cancel()
            activeJob.load()?.cancel()
        }

        override fun isExecuted(): Boolean = executed.load()

        override fun isCanceled(): Boolean = canceled.load()

        override fun timeout(): Timeout = Timeout.NONE

        override fun clone(): Call = AppHttpCall(initialRequest)

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> tag(type: KClass<T>): T? {
            return (tags[type] as? T) ?: initialRequest.tag(type)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> tag(type: Class<out T>): T? {
            return (tags[type] as? T) ?: initialRequest.tag(type)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T {
            initialRequest.tag(type)?.let { return it }
            return tags.computeIfAbsent(type) { computeIfAbsent() } as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T {
            initialRequest.tag(type)?.let { return it }
            return tags.computeIfAbsent(type) { computeIfAbsent() } as T
        }

        override fun addEventListener(eventListener: EventListener) {
            // No-op.
        }

        private suspend fun executeWithRedirects(): Response {
            val options = initialRequest.tag(HttpRequestOptions::class.java) ?: HttpRequestOptions.DEFAULT
            val pin = options.pinnedSha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            var current = initialRequest
            repeat(MAX_REQUESTS) {
                if (canceled.load()) {
                    throw IOException("Canceled")
                }
                if (options.restrictedTls && !current.url.isHttps) {
                    throw IOException("TLS 1.3 requires HTTPS")
                }
                val call = newInnerCall(current, options.proxy, options.restrictedTls, pin)
                activeCall.store(call)
                if (canceled.load()) {
                    call.cancel()
                    throw IOException("Canceled")
                }
                val response = withContext(Dispatchers.IO) {
                    call.execute()
                }
                val destination = try {
                    response.redirectDestination(current.url)
                } catch (error: Throwable) {
                    response.close()
                    throw error
                }
                if (destination == null) {
                    // Caller (or Retrofit) owns closing the terminal response.
                    return when {
                        response.isSuccessful -> response
                        response.code in options.emptyBodyStatusCodes -> response.withEmptyBody()
                        else -> response.withBoundedBody(STATUS_ERROR_PREVIEW_BYTES + 1L) { canceled.load() }
                    }
                }
                response.close()
                current = redirectRequest(current, destination, options.sameOriginRedirects, pin)
            }
            throw IOException("Too many HTTP redirects")
        }
    }

    private fun Response.withEmptyBody(): Response {
        val original = body
        val empty = object : ResponseBody() {
            override fun contentType(): MediaType? = original.contentType()

            override fun contentLength(): Long = 0L

            override fun source(): BufferedSource = Buffer()

            override fun close() {
                original.close()
            }
        }
        return newBuilder().body(empty).build()
    }

    private fun Response.withBoundedBody(
        maxBytes: Long,
        isCanceled: () -> Boolean,
    ): Response {
        val original = body
        val source = object : ForwardingSource(original.source()) {
            private var remaining = maxBytes

            override fun read(sink: Buffer, byteCount: Long): Long {
                if (remaining <= 0L) return -1L
                val read = try {
                    super.read(sink, minOf(byteCount, remaining))
                } catch (e: IOException) {
                    if (isCanceled()) throw e
                    remaining = 0L
                    return -1L
                }
                if (read > 0L) {
                    remaining -= read
                }
                return read
            }

            override fun close() {
                original.close()
            }
        }.buffer()
        val bounded = object : ResponseBody() {
            override fun contentType(): MediaType? = original.contentType()

            override fun contentLength(): Long {
                val length = original.contentLength()
                return if (length < 0L) {
                    -1L
                } else {
                    minOf(length, maxBytes)
                }
            }

            override fun source(): BufferedSource = source

            override fun close() {
                original.close()
            }
        }
        return newBuilder().body(bounded).build()
    }

    private companion object {
        const val MAX_REQUESTS = 10
        const val PROXY_AUTHORIZATION = "Proxy-Authorization"
        val TLS_13 = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3)
            .build()
    }
}

internal fun canUseHttpEngineForCert(certProvider: Int, allowSystemCert: Boolean): Boolean {
    return allowSystemCert || certProvider == CertProvider.SYSTEM
}

internal const val STATUS_ERROR_PREVIEW_BYTES = 100

internal fun HttpUrl.withoutCredentials(): HttpUrl = newBuilder().username("").password("").build()

internal fun HttpUrl.basicAuthorization(): String? {
    return if (encodedUsername.isNotEmpty() || encodedPassword.isNotEmpty()) {
        Credentials.basic(username, password, UTF_8)
    } else {
        null
    }
}

internal fun HttpUrl.sameOrigin(other: HttpUrl): Boolean {
    return scheme == other.scheme && host == other.host && port == other.port
}

private fun Response.redirectDestination(from: HttpUrl): HttpUrl? = when (code) {
    HttpStatus.MOVED_PERMANENTLY,
    HttpStatus.FOUND,
    HttpStatus.SEE_OTHER,
    HttpStatus.TEMPORARY_REDIRECT,
    HttpStatus.PERMANENT_REDIRECT,
        -> header("Location")?.let(from::resolve)

    else -> null
}

internal suspend fun <T : Any> withHttpDeadline(timeout: Duration, block: suspend () -> T): T {
    return withTimeoutOrNull(timeout) { block() }
        ?: throw SocketTimeoutException("HTTP request timed out")
}

private fun X509Certificate.matchesPin(pin: String): Boolean = encoded.toByteString().sha256().hex() == pin

@Suppress("CustomX509TrustManager")
private class PinnedLeafTrust(
    private val delegate: X509TrustManager,
    private val pin: String,
) : X509TrustManager by delegate {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        if (chain.firstOrNull()?.matchesPin(pin) != true) {
            delegate.checkServerTrusted(chain, authType)
        }
    }
}
