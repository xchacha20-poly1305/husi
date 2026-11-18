package fr.husi.core

import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcStatus
import fr.husi.CORE_SOCKET_NAME
import fr.husi.ktx.inputStream
import fr.husi.ktx.wrapIPV6Host
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.path
import io.github.vinceglb.filekit.resolve
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.Proxy
import java.net.Socket
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.SocketFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private const val WINDOWS_PIPE_PREFIX = """\\.\pipe\"""

internal fun localCoreSocketPath(basePath: String): String {
    return if (isCorePipePath(basePath)) {
        basePath
    } else {
        PlatformFile(basePath).resolve(CORE_SOCKET_NAME).path
    }
}

internal fun isCorePipePath(path: String): Boolean {
    return path.startsWith(WINDOWS_PIPE_PREFIX, ignoreCase = true)
}

internal expect fun newLocalCoreSocket(path: String): Socket

private class CoreSocketFactory(private val path: String) : SocketFactory() {
    override fun createSocket(): Socket = newLocalCoreSocket(path)

    // OkHttp always uses the unconnected overload.
    override fun createSocket(host: String, port: Int): Socket = unsupported()
    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = unsupported()

    override fun createSocket(host: InetAddress, port: Int): Socket = unsupported()
    override fun createSocket(
        host: InetAddress,
        port: Int,
        localHost: InetAddress,
        localPort: Int,
    ): Socket = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Use an unconnected core socket")
}

/** Where a remote daemon listens, as parsed from its URL. */
internal data class RemoteCoreAddress(val host: String, val port: Int, val tls: Boolean) {
    /** `host:port`, with an IPv6 literal back in brackets. */
    val authority: String
        get() = "${host.wrapIPV6Host()}:$port"
}

/** `http` or `https` only, port 80 / 443 when absent, as `daemon.NewRemoteClient` accepts. */
internal fun remoteCoreAddress(serverUrl: String): RemoteCoreAddress {
    val url = try {
        serverUrl.toHttpUrl()
    } catch (e: IllegalArgumentException) {
        throw CoreRpcException(GrpcStatus.INVALID_ARGUMENT, "invalid server URL: $serverUrl", e)
    }
    return RemoteCoreAddress(url.host, url.port, url.isHttps)
}

internal fun openLocalCoreChannel(basePath: String): CoreChannel =
    openCoreChannel(
        baseUrl = "http://localhost",
        builder = coreHttpClient()
            .protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
            .socketFactory(CoreSocketFactory(localCoreSocketPath(basePath)))
            .dns { listOf(InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))) },
    )

internal fun openRemoteCoreChannel(serverUrl: String, secret: String, roots: PlatformFile?): CoreChannel {
    val address = remoteCoreAddress(serverUrl)
    val builder = coreHttpClient()
    if (!address.tls) {
        builder.protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE))
    } else if (roots != null) {
        val trust = CoreTrustManager(roots)
        builder.sslSocketFactory(CoreTlsSocketFactory(trust), trust)
    }
    builder.addInterceptor { chain ->
        val request = chain.request().newBuilder().header("accept-language", "en")
        if (secret.isNotEmpty()) {
            request.header("authorization", "Bearer $secret")
        }
        chain.proceed(request.build())
    }
    val scheme = if (address.tls) "https" else "http"
    return openCoreChannel("$scheme://${address.authority}", builder)
}

private fun coreHttpClient() = OkHttpClient.Builder()
    .proxy(Proxy.NO_PROXY)
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(0, TimeUnit.SECONDS)
    .writeTimeout(0, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .retryOnConnectionFailure(false)
    .dispatcher(Dispatcher().apply {
        // Wire reads stream bodies on the dispatcher threads. Leave room for unary calls.
        maxRequestsPerHost = maxRequests
    })

private fun openCoreChannel(baseUrl: String, builder: OkHttpClient.Builder): CoreChannel {
    val closed = AtomicBoolean()
    val http = builder.addInterceptor { chain ->
        if (closed.get()) {
            throw IOException("Core channel closed")
        }
        chain.proceed(chain.request())
    }.build()
    http.dispatcher.idleCallback = Runnable {
        if (closed.get()) {
            http.connectionPool.evictAll()
        }
    }
    val grpc = GrpcClient.Builder()
        .client(http)
        .baseUrl(baseUrl)
        .minMessageToCompress(Long.MAX_VALUE)
        .build()
    return CoreChannel(grpc) {
        closed.set(true)
        http.dispatcher.cancelAll()
        http.connectionPool.evictAll()
    }
}

private class CoreTlsSocketFactory(private val trust: X509TrustManager) : SSLSocketFactory() {
    // A new context prevents TLS session resumption from skipping the trust check on reconnect.
    private fun factory(): SSLSocketFactory = SSLContext.getInstance("TLS").run {
        init(null, arrayOf(trust), null)
        socketFactory
    }

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        factory().createSocket(socket, host, port, autoClose)

    override fun createSocket(): Socket = factory().createSocket()
    override fun createSocket(host: String, port: Int): Socket = factory().createSocket(host, port)
    override fun createSocket(host: InetAddress, port: Int): Socket = factory().createSocket(host, port)

    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket =
        factory().createSocket(host, port, local, localPort)

    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket =
        factory().createSocket(host, port, local, localPort)

    override fun getDefaultCipherSuites(): Array<String> = factory().defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = factory().supportedCipherSuites
}

/** Loads the selected roots on every new TLS handshake, including after a reconnect. */
@Suppress("CustomX509TrustManager")
internal class CoreTrustManager(private val pemFile: PlatformFile) : X509TrustManager {
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) =
        load().checkServerTrusted(chain, authType)

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        load().checkClientTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        // OkHttp constructs its chain cleaner before connecting. The trust decision is made
        // by JSSE via checkServerTrusted, with the roots read at handshake time.
        emptyArray()

    private fun load(): X509TrustManager {
        try {
            return rootCertificatesTrustManager(pemFile)
        } catch (e: IOException) {
            throw CertificateException("read root certificates $pemFile", e)
        } catch (e: GeneralSecurityException) {
            throw CertificateException("load root certificates $pemFile", e)
        }
    }
}

/** The roots selected by the app, as exported by the core. */
internal fun rootCertificatesTrustManager(pemFile: PlatformFile): X509TrustManager {
    return pemFile.inputStream().use(::rootCertificatesTrustManager)
}

internal fun rootCertificatesTrustManager(pem: InputStream): X509TrustManager {
    val certificates = CertificateFactory.getInstance("X.509").generateCertificates(pem)
    if (certificates.isEmpty()) {
        throw IOException("empty root certificate bundle")
    }
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)
    certificates.forEachIndexed { index, certificate ->
        keyStore.setCertificateEntry("root-$index", certificate)
    }
    return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
        init(keyStore)
        trustManagers.filterIsInstance<X509TrustManager>().single()
    }
}
