package fr.husi.libcore

import fr.husi.CertProvider
import fr.husi.bg.AndroidPlatformInterface
import fr.husi.database.DataStore
import fr.husi.ktx.toStringIterator
import java.security.KeyStore
import kotlin.io.encoding.Base64

actual fun createBoxService(isBgProcess: Boolean): Service? {
    return if (isBgProcess) {
        Libcore.newService(AndroidPlatformInterface())
    } else {
        null
    }
}

actual fun loadCA(provider: Int) {
    var certOption = 0
    var certList: StringIterator? = null
    when (DataStore.certProvider) {
        CertProvider.SYSTEM -> {
            certOption = Libcore.CertGoOrigin
        }

        CertProvider.MOZILLA -> {
            certOption = Libcore.CertMozilla
        }

        CertProvider.SYSTEM_AND_USER -> {
            certOption = Libcore.CertWithUserTrust
            certList = loadCertFromJava().let {
                it.toStringIterator(it.size)
            }
        }

        CertProvider.CHROME -> {
            certOption = Libcore.CertChrome
        }
    }
    Libcore.updateRootCACerts(certOption, certList)
}

private fun loadCertFromJava(): List<String> {
    val certificates = mutableListOf<String>()
    val keyStore = KeyStore.getInstance("AndroidCAStore")
    if (keyStore != null) {
        keyStore.load(null, null)
        val aliases = keyStore.aliases()
        while (aliases.hasMoreElements()) {
            val cert = keyStore.getCertificate(aliases.nextElement())
            certificates.add(
                "-----BEGIN CERTIFICATE-----\n" + Base64.encode(cert.encoded) + "\n-----END CERTIFICATE-----",
            )
        }
    }
    return certificates
}