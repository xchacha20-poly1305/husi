package fr.husi.libcore

import fr.husi.CertProvider
import fr.husi.bg.DesktopPlatformInterface
import fr.husi.database.DataStore

actual fun createBoxService(isBgProcess: Boolean): Service? {
    return Libcore.newService(DesktopPlatformInterface())
}

actual fun loadCA(provider: Int) {
    val provider = when (DataStore.certProvider) {
        CertProvider.SYSTEM -> Libcore.CertGoOrigin
        CertProvider.MOZILLA -> Libcore.CertMozilla
        CertProvider.CHROME -> Libcore.CertChrome
        else -> Libcore.CertGoOrigin
    }
    Libcore.updateRootCACerts(provider, null)
}