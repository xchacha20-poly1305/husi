package fr.husi.core

import fr.husi.CORE_SOCKET_NAME
import fr.husi.io.readLeb128
import fr.husi.ktx.blankAsNull
import io.github.xchacha20_poly1305.kpuri.Url
import io.github.xchacha20_poly1305.kpuri.UrlSyntaxException
import io.github.xchacha20_poly1305.kurpc.ChannelConfig
import io.github.xchacha20_poly1305.kurpc.Connection
import io.github.xchacha20_poly1305.kurpc.Metadata
import io.github.xchacha20_poly1305.kurpc.Status
import io.github.xchacha20_poly1305.kurpc.Transport
import io.github.xchacha20_poly1305.kurpc.tls
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import kotlin.time.Duration.Companion.seconds

private const val WINDOWS_PIPE_PREFIX = """\\.\pipe\"""

/**
 * Where a local core listens: `basePath/api.sock`, or on Windows a named pipe whose full path
 * is [basePath] itself (the daemon's protected pipe). Same rule as `coresvc.SocketPath` on the
 * host side.
 */
internal fun localCoreTransport(basePath: String): Transport =
    if (basePath.startsWith(WINDOWS_PIPE_PREFIX, ignoreCase = true)) {
        Transport.WindowsNamedPipe(basePath)
    } else {
        Transport.Unix(File(basePath, CORE_SOCKET_NAME).path)
    }

/** Where a remote daemon listens, as parsed from its URL. */
internal data class RemoteCoreAddress(val host: String, val port: Int, val tls: Boolean) {
    /** `host:port`, with an IPv6 literal back in brackets. */
    val authority: String
        get() = if (':' in host) "[$host]:$port" else "$host:$port"
}

/** `http` or `https` only, port 80 / 443 when absent, as `daemon.NewRemoteClient` accepts. */
internal fun remoteCoreAddress(serverUrl: String): RemoteCoreAddress {
    val url = try {
        Url.parse(serverUrl)
    } catch (e: UrlSyntaxException) {
        throw invalidUrl("invalid server URL: $serverUrl", e)
    }
    val tls = when (url.scheme) {
        "http" -> false
        "https" -> true
        else -> throw invalidUrl("invalid server URL scheme: ${url.scheme}, expected http or https")
    }
    val host = url.host.blankAsNull()
        ?: throw invalidUrl("missing host in server URL: $serverUrl")
    val port = url.port?.toIntOrNull()
        ?: if (tls) 443 else 80
    return RemoteCoreAddress(host, port, tls)
}

private val REMOTE_CONNECT_TIMEOUT = 15.seconds

/**
 * A remote sing-box daemon, dialed the way `daemon.NewRemoteClient` does: TLS verified against
 * the URL's host with the roots [trust] returns (asked on every dial), `authorization: Bearer`
 * when [secret] is set, and the `accept-language` its locale interceptor adds (sing-box's
 * default locale, as husi never set another).
 */
internal fun remoteCoreChannelConfig(
    serverUrl: String,
    secret: String,
    trust: () -> SSLSocketFactory,
): ChannelConfig {
    val address = remoteCoreAddress(serverUrl)
    var metadata = Metadata.of("accept-language" to "en")
    if (secret.isNotEmpty()) {
        metadata += Metadata.of("authorization" to "Bearer $secret")
    }
    return ChannelConfig(
        transport = if (address.tls) {
            tlsTransport(address, trust)
        } else {
            Transport.Tcp(address.host, address.port)
        },
        authority = address.authority,
        secure = address.tls,
        metadata = metadata,
        connectTimeout = REMOTE_CONNECT_TIMEOUT,
    )
}

/** kurpc speaks plain HTTP/2; TLS is a transport, run here by the platform's JSSE. */
private fun tlsTransport(address: RemoteCoreAddress, trust: () -> SSLSocketFactory): Transport = Transport.Custom {
    withContext(Dispatchers.IO) {
        val sslSocketFactory = trust()
        val socket = Socket()
        try {
            socket.connect(
                InetSocketAddress(address.host, address.port),
                REMOTE_CONNECT_TIMEOUT.inWholeMilliseconds.toInt(),
            )
        } catch (e: IOException) {
            socket.close()
            throw e
        }
        Connection.tls(socket, address.host, sslSocketFactory)
    }
}

/**
 * Trusts exactly the roots in [pemFile]: `externalAssets/plugin-ca.pem`, the root set of the
 * user's `certProvider` choice (plus `ca.pem` on Android), the same roots the core trusts. A
 * missing or empty file fails the dial rather than falling back to the platform store, which
 * would silently widen or narrow what is trusted.
 */
internal fun rootCertificatesSocketFactory(pemFile: File): SSLSocketFactory {
    val certificates = try {
        pemFile.inputStream().use { CertificateFactory.getInstance("X.509").generateCertificates(it) }
    } catch (e: IOException) {
        throw IOException("read root certificates $pemFile", e)
    }
    if (certificates.isEmpty()) {
        throw IOException("no root certificates in $pemFile")
    }
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)
    certificates.forEachIndexed { index, certificate ->
        keyStore.setCertificateEntry("root-$index", certificate)
    }
    val trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagers.init(keyStore)
    val context = SSLContext.getInstance("TLS")
    context.init(null, trustManagers.trustManagers, null)
    return context.socketFactory
}

private fun invalidUrl(message: String, cause: Throwable? = null) =
    CoreRpcException(Status.Code.INVALID_ARGUMENT, message, cause)

/**
 * `status` (field 1, an enum) of a `grpc.health.v1.HealthCheckResponse`, 0 (UNKNOWN) when
 * absent. Read by hand so husi needs no health proto for one field.
 */
internal fun healthCheckStatus(response: ByteArray): Int {
    val message = Buffer().write(response)
    var status = 0
    try {
        while (!message.exhausted()) {
            val key = message.readLeb128()
            val field = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> message.readLeb128().let { if (field == 1) status = it.toInt() }
                1 -> message.skip(8)
                2 -> message.skip(message.readLeb128())
                5 -> message.skip(4)
                else -> throw IOException("unknown wire type in key $key")
            }
        }
    } catch (e: IOException) {
        throw CoreRpcException(Status.Code.INTERNAL, "malformed health check response", e)
    }
    return status
}
