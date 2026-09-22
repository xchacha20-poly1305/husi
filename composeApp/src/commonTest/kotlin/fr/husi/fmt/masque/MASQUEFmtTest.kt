package fr.husi.fmt.masque

import fr.husi.fmt.HttpVersion
import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.parseOutbound
import fr.husi.ktx.JSONMap
import fr.husi.ktx.applyDefaultValues
import fr.husi.ktx.asKxsMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MASQUEFmtTest {

    @Test
    fun `buildSingBoxEndpointMASQUEBean should default to HTTP 3 with TLS`() {
        val bean = MASQUEBean().applyDefaultValues()

        val endpoint = buildSingBoxEndpointMASQUEBean(bean)

        assertEquals(SingBoxOptions.TYPE_MASQUE_CLIENT, endpoint.type)
        assertEquals("127.0.0.1", endpoint.server)
        assertEquals(443, endpoint.server_port)
        assertEquals(HttpVersion.HTTP_3, endpoint.version)
        assertEquals(MASQUEBean.DEFAULT_MTU, endpoint.mtu)
        assertEquals(false, endpoint.disable_version_fallback)
        assertNull(endpoint.username)
        assertNull(endpoint.password)
        assertNull(endpoint.path)
        assertNull(endpoint.headers)
        val tls = assertNotNull(endpoint.tls)
        assertEquals(true, tls.enabled)
    }

    @Test
    fun `buildSingBoxEndpointMASQUEBean should omit TLS when disabled`() {
        val bean = MASQUEBean().apply {
            serverAddress = "plain.example.com"
            serverPort = 443
            enableTLS = false
            httpVersion = HttpVersion.HTTP_2
            username = "user"
            password = "secret"
            path = "/connect"
            headers = "x-token:abc"
            mtu = 1400
        }

        val endpoint = buildSingBoxEndpointMASQUEBean(bean)

        assertNull(endpoint.tls)
        assertEquals(HttpVersion.HTTP_2, endpoint.version)
        assertEquals("user", endpoint.username)
        assertEquals("secret", endpoint.password)
        assertEquals("/connect", endpoint.path)
        assertEquals(listOf("abc"), endpoint.headers?.get("x-token")?.toList())
        assertEquals(1400, endpoint.mtu)
    }

    @Test
    fun `parseMASQUEEndpoint should map a full masque-client endpoint`() {
        val json = fullEndpoint(version = 2L)

        val bean = assertIs<MASQUEBean>(parseOutbound(json))

        assertEquals("node-a", bean.name)
        assertEquals("edge.example.com", bean.serverAddress)
        assertEquals(8443, bean.serverPort)
        assertEquals("user", bean.username)
        assertEquals("secret", bean.password)
        assertEquals("/masque", bean.path)
        assertEquals("x-token:abc", bean.headers)
        assertEquals(HttpVersion.HTTP_2, bean.httpVersion)
        assertTrue(bean.disableVersionFallback)
        assertEquals(1400, bean.mtu)
        assertTrue(bean.enableTLS)
        assertEquals("sni.example.com", bean.serverName)
        assertTrue(bean.allowInsecure)
        assertTrue(bean.disableSNI)
        assertEquals("h2,http/1.1", bean.alpn)
        assertEquals("cert-a\ncert-b", bean.certificates)
        assertEquals("client-cert", bean.clientCert)
        assertEquals("client-key-a\nclient-key-b", bean.clientKey)
        assertEquals("sha-a\nsha-b", bean.certPublicKeySha256)
        assertEquals(SingBoxOptions.FINGERPRINT_CHROME, bean.utlsFingerprint)
        assertTrue(bean.tlsFragment)
        assertEquals("200ms", bean.tlsFragmentFallbackDelay)
        assertTrue(bean.tlsRecordFragment)
        assertEquals("spoof.example.com", bean.tlsSpoof)
        assertEquals("wrong-checksum", bean.tlsSpoofMethod)
        assertTrue(bean.ech)
        assertEquals("cfg-1\ncfg-2", bean.echConfig)
        assertEquals("ech.example.com", bean.echQueryServerName)
    }

    @Test
    fun `parseMASQUEEndpoint should use HTTP 3 when version is absent or zero`() {
        val absent = parseMASQUEEndpoint(
            mutableMapOf(
                "type" to SingBoxOptions.TYPE_MASQUE_CLIENT,
                "server" to "example.com",
                "server_port" to 443L,
            ),
        )
        assertEquals(HttpVersion.HTTP_3, absent.httpVersion)
        assertFalse(absent.enableTLS)

        val zero = parseMASQUEEndpoint(
            mutableMapOf(
                "type" to SingBoxOptions.TYPE_MASQUE_CLIENT,
                "server" to "example.com",
                "server_port" to 443L,
                "version" to 0L,
                "tls" to mutableMapOf("enabled" to true),
            ),
        )
        assertEquals(HttpVersion.HTTP_3, zero.httpVersion)
        assertTrue(zero.enableTLS)
    }

    @Test
    fun `parseMASQUEEndpoint should turn TLS off when enabled is not true`() {
        val bean = parseMASQUEEndpoint(
            mutableMapOf(
                "server" to "example.com",
                "server_port" to 443L,
                "tls" to mutableMapOf(
                    "enabled" to false,
                    "server_name" to "should-not-be-set",
                ),
            ),
        )

        assertFalse(bean.enableTLS)
        assertEquals("", bean.serverName)
    }

    @Test
    fun `build then parse should round trip MASQUE fields`() {
        val source = MASQUEBean().apply {
            name = "node-a"
            serverAddress = "edge.example.com"
            serverPort = 8443
            username = "user"
            password = "secret"
            path = "/masque"
            headers = "x-token:abc"
            httpVersion = HttpVersion.HTTP_2
            disableVersionFallback = true
            enableTLS = true
            mtu = 1400
            serverName = "sni.example.com"
            allowInsecure = true
            disableSNI = true
            alpn = "h2,http/1.1"
            certificates = "cert-a\ncert-b"
            clientCert = "client-cert"
            clientKey = "client-key-a\nclient-key-b"
            certPublicKeySha256 = "sha-a\nsha-b"
            utlsFingerprint = SingBoxOptions.FINGERPRINT_CHROME
            tlsFragment = true
            tlsFragmentFallbackDelay = "200ms"
            tlsRecordFragment = true
            tlsSpoof = "spoof.example.com"
            tlsSpoofMethod = "wrong-checksum"
            ech = true
            echConfig = "cfg-1\ncfg-2"
            echQueryServerName = "ech.example.com"
        }

        val restored = parseMASQUEEndpoint(
            buildSingBoxEndpointMASQUEBean(source).apply { tag = source.name }.asKxsMap(),
        )

        assertEquals(source.name, restored.name)
        assertEquals(source.serverAddress, restored.serverAddress)
        assertEquals(source.serverPort, restored.serverPort)
        assertEquals(source.username, restored.username)
        assertEquals(source.password, restored.password)
        assertEquals(source.path, restored.path)
        assertEquals(source.headers, restored.headers)
        assertEquals(source.httpVersion, restored.httpVersion)
        assertEquals(source.disableVersionFallback, restored.disableVersionFallback)
        assertEquals(source.enableTLS, restored.enableTLS)
        assertEquals(source.mtu, restored.mtu)
        assertEquals(source.serverName, restored.serverName)
        assertEquals(source.allowInsecure, restored.allowInsecure)
        assertEquals(source.disableSNI, restored.disableSNI)
        assertEquals(source.alpn, restored.alpn)
        assertEquals(source.certificates, restored.certificates)
        assertEquals(source.clientCert, restored.clientCert)
        assertEquals(source.clientKey, restored.clientKey)
        assertEquals(source.certPublicKeySha256, restored.certPublicKeySha256)
        assertEquals(source.utlsFingerprint, restored.utlsFingerprint)
        assertEquals(source.tlsFragment, restored.tlsFragment)
        assertEquals(source.tlsFragmentFallbackDelay, restored.tlsFragmentFallbackDelay)
        assertEquals(source.tlsRecordFragment, restored.tlsRecordFragment)
        assertEquals(source.tlsSpoof, restored.tlsSpoof)
        assertEquals(source.tlsSpoofMethod, restored.tlsSpoofMethod)
        assertEquals(source.ech, restored.ech)
        assertEquals(source.echConfig, restored.echConfig)
        assertEquals(source.echQueryServerName, restored.echQueryServerName)
    }

    @Test
    fun `serialize then deserialize should round trip MASQUE fields`() {
        val source = MASQUEBean().apply {
            name = "node-a"
            serverAddress = "edge.example.com"
            serverPort = 8443
            username = "user"
            password = "secret"
            path = "/masque"
            headers = "x-token:abc"
            httpVersion = HttpVersion.HTTP_2
            disableVersionFallback = true
            enableTLS = false
            mtu = 1400
            serverName = "sni.example.com"
            alpn = "h2"
            certificates = "cert-a"
            certPublicKeySha256 = "sha-a"
            utlsFingerprint = SingBoxOptions.FINGERPRINT_FIREFOX
            allowInsecure = true
            disableSNI = true
            tlsFragment = true
            tlsFragmentFallbackDelay = "200ms"
            tlsRecordFragment = false
            ech = true
            echConfig = "cfg-1"
            echQueryServerName = "ech.example.com"
            clientCert = "client-cert"
            clientKey = "client-key"
            tlsSpoof = "spoof.example.com"
            tlsSpoofMethod = "wrong-checksum"
        }

        val restored = source.clone()

        assertEquals(source.name, restored.name)
        assertEquals(source.serverAddress, restored.serverAddress)
        assertEquals(source.serverPort, restored.serverPort)
        assertEquals(source.username, restored.username)
        assertEquals(source.password, restored.password)
        assertEquals(source.path, restored.path)
        assertEquals(source.headers, restored.headers)
        assertEquals(HttpVersion.HTTP_2, restored.httpVersion)
        assertTrue(restored.disableVersionFallback)
        assertFalse(restored.enableTLS)
        assertEquals(source.mtu, restored.mtu)
        assertEquals(source.serverName, restored.serverName)
        assertEquals(source.alpn, restored.alpn)
        assertEquals(source.certificates, restored.certificates)
        assertEquals(source.certPublicKeySha256, restored.certPublicKeySha256)
        assertEquals(source.utlsFingerprint, restored.utlsFingerprint)
        assertEquals(source.allowInsecure, restored.allowInsecure)
        assertEquals(source.disableSNI, restored.disableSNI)
        assertEquals(source.tlsFragment, restored.tlsFragment)
        assertEquals(source.tlsFragmentFallbackDelay, restored.tlsFragmentFallbackDelay)
        assertEquals(source.tlsRecordFragment, restored.tlsRecordFragment)
        assertEquals(source.ech, restored.ech)
        assertEquals(source.echConfig, restored.echConfig)
        assertEquals(source.echQueryServerName, restored.echQueryServerName)
        assertEquals(source.clientCert, restored.clientCert)
        assertEquals(source.clientKey, restored.clientKey)
        assertEquals(source.tlsSpoof, restored.tlsSpoof)
        assertEquals(source.tlsSpoofMethod, restored.tlsSpoofMethod)
    }

    private fun fullEndpoint(version: Long): JSONMap = mutableMapOf(
        "type" to SingBoxOptions.TYPE_MASQUE_CLIENT,
        "tag" to "node-a",
        "server" to "edge.example.com",
        "server_port" to 8443L,
        "username" to "user",
        "password" to "secret",
        "path" to "/masque",
        "headers" to mutableMapOf<String, Any?>("X-Token" to listOf("abc")),
        "version" to version,
        "disable_version_fallback" to true,
        "mtu" to 1400L,
        "tls" to mutableMapOf(
            "enabled" to true,
            "server_name" to "sni.example.com",
            "insecure" to true,
            "disable_sni" to true,
            "alpn" to listOf("h2", "http/1.1"),
            "certificate" to listOf("cert-a", "cert-b"),
            "client_certificate" to "client-cert",
            "client_key" to listOf("client-key-a", "client-key-b"),
            "certificate_public_key_sha256" to listOf("sha-a", "sha-b"),
            "utls" to mutableMapOf(
                "enabled" to true,
                "fingerprint" to SingBoxOptions.FINGERPRINT_CHROME,
            ),
            "fragment" to true,
            "fragment_fallback_delay" to "200ms",
            "record_fragment" to true,
            "spoof" to "spoof.example.com",
            "spoof_method" to "wrong-checksum",
            "ech" to mutableMapOf(
                "enabled" to true,
                "config" to listOf("cfg-1", "cfg-2"),
                "query_server_name" to "ech.example.com",
            ),
        ),
    )
}
