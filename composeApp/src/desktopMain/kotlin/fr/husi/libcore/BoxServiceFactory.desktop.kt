package fr.husi.libcore

import fr.husi.CertProvider
import fr.husi.bg.DesktopPlatformInterface

actual fun createBoxService(isBgProcess: Boolean): Service? {
    return Libcore.newService(DesktopPlatformInterface())
}

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
