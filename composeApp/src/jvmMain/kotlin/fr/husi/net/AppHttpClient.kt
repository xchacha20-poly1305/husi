package fr.husi.net

import fr.husi.core.rootCertificatesTrustManager
import fr.husi.ktx.inputStream
import fr.husi.ktx.readBytesBlocking
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.isRegularFile
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.TlsVersion
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets.UTF_8
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal val HTTP_TIMEOUT = 15_000.milliseconds

internal expect fun appTlsContext(trust: X509TrustManager, restrictedTls: Boolean): SSLContext

internal class AppHttpClient(
    private val rootsFile: PlatformFile,
    private val prepareRoots: suspend () -> Unit = {},
) {
    private val base = OkHttpClient.Builder()
        .proxy(Proxy.NO_PROXY)
        .connectTimeout(HTTP_TIMEOUT)
        .readTimeout(HTTP_TIMEOUT)
        .writeTimeout(HTTP_TIMEOUT)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private class Roots(val pem: ByteString) {
        val trust = rootCertificatesTrustManager(pem.toByteArray().inputStream())
        val socketFactory = appTlsContext(trust, restrictedTls = false).socketFactory
    }

    private var cachedRoots: Roots? = null

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

    private suspend fun newCall(
        request: Request,
        proxy: LocalHttpProxy?,
        restrictedTls: Boolean,
        pin: String?,
    ): Call {
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

    suspend fun <T> exchange(
        request: Request,
        proxy: LocalHttpProxy?,
        restrictedTls: Boolean = false,
        pinnedSha256: String? = null,
        sameOriginRedirects: Boolean = false,
        consume: suspend (Response, () -> Unit) -> T,
    ): T {
        val pin = pinnedSha256?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        var current = request
        repeat(MAX_REQUESTS) {
            if (restrictedTls && !current.url.isHttps) {
                throw IOException("TLS 1.3 requires HTTPS")
            }
            val call = newCall(current, proxy, restrictedTls, pin)
            val result = call.withResponse { response ->
                val destination = response.redirectDestination(current.url)
                if (destination == null) {
                    ExchangeResult.Body(consume(response, call::cancel))
                } else {
                    redirectExchange(current, destination, sameOriginRedirects, pin)
                }
            }
            when (result) {
                is ExchangeResult.Body -> return result.value
                is ExchangeResult.Redirect -> current = result.request
            }
        }
        throw IOException("Too many HTTP redirects")
    }

    private fun redirectExchange(
        current: Request,
        destination: HttpUrl,
        sameOriginRedirects: Boolean,
        pin: String?,
    ): ExchangeResult.Redirect {
        val sameOrigin = current.url.sameOrigin(destination)
        if (!sameOrigin && (sameOriginRedirects || pin != null)) {
            throw IOException("Cross-origin redirect")
        }
        val next = current.newBuilder().url(destination.withoutCredentials())
        if (!sameOrigin) {
            next.removeHeader("Authorization")
            next.removeHeader("Cookie")
        }
        return ExchangeResult.Redirect(next.build())
    }

    private sealed interface ExchangeResult<out T> {
        data class Body<T>(val value: T) : ExchangeResult<T>
        data class Redirect(val request: Request) : ExchangeResult<Nothing>
    }

    private companion object {
        const val MAX_REQUESTS = 10
        const val PROXY_AUTHORIZATION = "Proxy-Authorization"
        val TLS_13 = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_3)
            .build()
    }
}

private const val STATUS_ERROR_PREVIEW_BYTES = 100

internal fun HttpUrl.withoutCredentials(): HttpUrl = newBuilder().username("").password("").build()

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

internal fun Response.statusError(): IOException {
    val status = "HTTP $code $message".trimEnd()
    return try {
        val preview = peekBody(STATUS_ERROR_PREVIEW_BYTES.toLong() + 1).bytes()
        val detail = preview.decodeToString(
            endIndex = minOf(STATUS_ERROR_PREVIEW_BYTES, preview.size),
        )
        val suffix = if (preview.size > STATUS_ERROR_PREVIEW_BYTES) {
            " ..."
        } else {
            ""
        }
        IOException(
            if (preview.isEmpty()) {
                status
            } else {
                "$status: $detail$suffix"
            },
        )
    } catch (error: IOException) {
        IOException(status, error)
    }
}

internal suspend fun <T : Any> withHttpDeadline(timeout: Duration, block: suspend () -> T): T {
    return withTimeoutOrNull(timeout) { block() }
        ?: throw SocketTimeoutException("HTTP request timed out")
}

private suspend fun <T> Call.withResponse(consume: suspend (Response) -> T): T = coroutineScope {
    // Keep cancellation wired through the last body read, including redirects and streaming copies.
    val cancelOnExit = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
        try {
            awaitCancellation()
        } finally {
            this@withResponse.cancel()
        }
    }
    try {
        withContext(Dispatchers.IO) {
            execute().use {
                consume(it)
            }
        }
    } catch (error: IOException) {
        ensureActive()
        throw error
    } finally {
        cancelOnExit.cancel()
    }
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
