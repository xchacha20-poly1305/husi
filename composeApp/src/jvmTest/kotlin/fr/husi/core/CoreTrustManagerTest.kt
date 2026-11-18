package fr.husi.core

import fr.husi.ktx.deleteRecursivelyBlocking
import fr.husi.ktx.writeText
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.resolve
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.security.KeyFactory
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import kotlin.concurrent.thread
import kotlin.io.encoding.Base64
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class CoreTrustManagerTest {

    private val directory: PlatformFile = PlatformFile(createTempDirectory("husi-root-certificates-test").toString())

    @AfterTest
    fun deleteDirectory() {
        directory.deleteRecursivelyBlocking()
    }

    @Test
    fun `a server certificate among the roots passes the handshake`() = runTest {
        val roots = pemFile(OTHER_CERTIFICATE + LOCALHOST_CERTIFICATE)
        handshakeWithLocalhostServer(rootCertificatesTrustManager(roots))
    }

    @Test
    fun `a server certificate outside the roots fails the handshake`() = runTest {
        val roots = pemFile(OTHER_CERTIFICATE)
        assertFailsWith<SSLException> {
            handshakeWithLocalhostServer(rootCertificatesTrustManager(roots))
        }
    }

    @Test
    fun `a missing or empty roots file fails instead of falling back to the platform store`() {
        assertFailsWith<IOException> { rootCertificatesTrustManager(directory.resolve("absent.pem")) }
        assertFailsWith<IOException> { rootCertificatesTrustManager(pemFile("")) }
    }

    private fun pemFile(content: String): PlatformFile =
        directory.resolve("roots.pem").apply { writeText(content) }

    private suspend fun handshakeWithLocalhostServer(trust: X509TrustManager) {
        val loopback = InetAddress.getLoopbackAddress()
        val server = localhostServerContext().serverSocketFactory
            .createServerSocket(0, 1, loopback) as SSLServerSocket
        server.use {
            val accepted = thread {
                runCatching {
                    (server.accept() as SSLSocket).use { it.startHandshake() }
                }
            }
            val client = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), null) }
            Socket(loopback, server.localPort).use { raw ->
                val tlsSocket = client.socketFactory.createSocket(
                    raw,
                    "localhost",
                    server.localPort,
                    true,
                ) as SSLSocket
                tlsSocket.use {
                    it.sslParameters = it.sslParameters.apply {
                        endpointIdentificationAlgorithm = "HTTPS"
                    }
                    it.startHandshake()
                }
            }
            accepted.join()
        }
    }

    private fun localhostServerContext(): SSLContext {
        val decodedKey = Base64.decode(LOCALHOST_PRIVATE_KEY_PKCS8)
        val key = KeyFactory.getInstance("EC")
            .generatePrivate(PKCS8EncodedKeySpec(decodedKey))
        val certificate = CertificateFactory.getInstance("X.509")
            .generateCertificate(LOCALHOST_CERTIFICATE.byteInputStream())
        val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
        keyStore.load(null, null)
        keyStore.setKeyEntry("localhost", key, CharArray(0), arrayOf(certificate))
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        keyManagers.init(keyStore, CharArray(0))
        return SSLContext.getInstance("TLS").apply {
            init(keyManagers.keyManagers, null, null)
        }
    }

    private companion object {
        // Self-signed P-256 certificate for DNS:localhost, valid until 2126.
        const val LOCALHOST_PRIVATE_KEY_PKCS8 =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgbI+dOvhoKH8/ALLi" +
                "lUPjFGQXM9l0a70X4uSaj1bEnJGhRANCAATjSZe7He4aLsD+C+UbGRIefFQX" +
                "t+bQb//ClwunXWhvZNb01VKjVieFlVajUMladlXBAfoGKM3Vw/HCyQSdULDr"

        val LOCALHOST_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIBqjCCAVCgAwIBAgIUC+KEjVSlgyKFadM94csUzFEAddwwCgYIKoZIzj0EAwIw
            FDESMBAGA1UEAwwJbG9jYWxob3N0MCAXDTI2MTAwNjEzMjMwNloYDzIxMjYwOTEy
            MTMyMzA2WjAUMRIwEAYDVQQDDAlsb2NhbGhvc3QwWTATBgcqhkjOPQIBBggqhkjO
            PQMBBwNCAATjSZe7He4aLsD+C+UbGRIefFQXt+bQb//ClwunXWhvZNb01VKjVieF
            lVajUMladlXBAfoGKM3Vw/HCyQSdULDro34wfDAdBgNVHQ4EFgQUQgNwtAmBXFVh
            agVpAipEIFnxZ2AwHwYDVR0jBBgwFoAUQgNwtAmBXFVhagVpAipEIFnxZ2AwDwYD
            VR0TAQH/BAUwAwEB/zAUBgNVHREEDTALgglsb2NhbGhvc3QwEwYDVR0lBAwwCgYI
            KwYBBQUHAwEwCgYIKoZIzj0EAwIDSAAwRQIhAMsqEBlbcr2o4O75eSPOwW+/AWCV
            de8rAXP34Y01JF6fAiAMB896poupvAQ+Sk+ECJy2IIO3mlXFR6i8DL4laW/Snw==
            -----END CERTIFICATE-----
        """.trimIndent() + "\n"

        // An unrelated self-signed certificate (CN=other.example).
        val OTHER_CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIBiDCCAS2gAwIBAgIUXCXEHjxx5kmQAPOWTTXe+QbKt2AwCgYIKoZIzj0EAwIw
            GDEWMBQGA1UEAwwNb3RoZXIuZXhhbXBsZTAgFw0yNjEwMDYxMzIzMThaGA8yMTI2
            MDkxMjEzMjMxOFowGDEWMBQGA1UEAwwNb3RoZXIuZXhhbXBsZTBZMBMGByqGSM49
            AgEGCCqGSM49AwEHA0IABNRaDmltbsn5WTuLcQ61v706/oYbKoXSCWA2ezjosID2
            Q70MzfidYmS4Rz2o53z+PCBkinqouOmGSeULybBZHDGjUzBRMB0GA1UdDgQWBBTo
            PkEa066rWAWBzumc8cC32QkpdTAfBgNVHSMEGDAWgBToPkEa066rWAWBzumc8cC3
            2QkpdTAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0kAMEYCIQDQl4b9cj+r
            29eruMKJynl7P/gDZh0nWh8YeduKmWTnMgIhAMwgmG50ggQ8Oo5teBh3WON5XZaI
            FhevARpxdiMD9Tgb
            -----END CERTIFICATE-----
        """.trimIndent() + "\n"
    }
}
