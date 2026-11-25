package fr.husi.net

import org.bouncycastle.jsse.provider.BouncyCastleJsseProvider
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

private val tlsProvider by lazy { BouncyCastleJsseProvider() }

internal actual fun appTlsContext(trust: X509TrustManager, restrictedTls: Boolean): SSLContext {
    val system = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trust), null)
    }
    if (!restrictedTls || "TLSv1.3" in system.supportedSSLParameters.protocols) {
        return system
    }
    return SSLContext.getInstance("TLS", tlsProvider).apply {
        init(null, arrayOf(trust), null)
    }
}
