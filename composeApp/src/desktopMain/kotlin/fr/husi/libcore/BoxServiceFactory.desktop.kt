package fr.husi.libcore

import fr.husi.CertProvider

/**
 * Desktop hosts the core out-of-process (`husi-core session`). The in-process
 * JNI [Service] is no longer created.
 */
actual fun createBoxService(isBgProcess: Boolean): Service? = null

actual fun loadCA(provider: Int) {
    val certOption = when (provider) {
        CertProvider.SYSTEM -> Libcore.CertSystem
        CertProvider.MOZILLA -> Libcore.CertMozilla
        CertProvider.SYSTEM_AND_USER -> Libcore.CertWithUserTrust
        CertProvider.CHROME -> Libcore.CertChrome
        else -> Libcore.CertSystem
    }
    Libcore.setupRootCA(certOption)
}
