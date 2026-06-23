package fr.husi.libcore

import fr.husi.CertProvider
import fr.husi.bg.AndroidPlatformInterface
import fr.husi.database.DataStore

actual fun createBoxService(isBgProcess: Boolean): Service? {
    return if (isBgProcess) {
        Libcore.newService(AndroidPlatformInterface())
    } else {
        null
    }
}

actual fun loadCA(provider: Int) {
    val certOption = when (DataStore.certProvider) {
        CertProvider.SYSTEM -> Libcore.CertSystem
        CertProvider.MOZILLA -> Libcore.CertMozilla
        CertProvider.SYSTEM_AND_USER -> Libcore.CertWithUserTrust
        CertProvider.CHROME -> Libcore.CertChrome
        else -> Libcore.CertSystem
    }
    Libcore.updateRootCACerts(certOption)
}