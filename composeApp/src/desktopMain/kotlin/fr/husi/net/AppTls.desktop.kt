package fr.husi.net

import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

internal actual fun appTlsContext(trust: X509TrustManager, restrictedTls: Boolean): SSLContext {
    return SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(trust), null)
    }
}
