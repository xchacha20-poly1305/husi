package fr.husi.net

import org.conscrypt.Conscrypt
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

private val tlsProvider by lazy { Conscrypt.newProvider() }

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
