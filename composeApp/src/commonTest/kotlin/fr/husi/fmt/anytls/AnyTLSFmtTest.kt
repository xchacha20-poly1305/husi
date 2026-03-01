package fr.husi.fmt.anytls

import fr.husi.fmt.FmtTestConstant
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnyTLSFmtTest {

    @Test
    fun `parseAnyTLS should parse host default port and query flags`() {
        val bean = parseAnyTLS(FmtTestConstant.ANYTLS_URL)

        assertEquals("example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("secret", bean.password)
        assertEquals("edge.example.com", bean.serverName)
        assertTrue(bean.allowInsecure)
    }

    @Test
    fun `toUri should preserve serializable fields through parseAnyTLS`() {
        val source = AnyTLSBean().apply {
            serverAddress = "gateway.example.com"
            serverPort = 8443
            password = "pwd"
            serverName = "sni.example.com"
            allowInsecure = true
        }

        val parsed = parseAnyTLS(source.toUri())

        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.password, parsed.password)
        assertEquals(source.serverName, parsed.serverName)
        assertEquals(source.allowInsecure, parsed.allowInsecure)
    }

    @Test
    fun `buildSingBoxOutboundAnyTLSBean should map all supported AnyTLS and TLS fields`() {
        val bean = AnyTLSBean().apply {
            serverAddress = "edge.example.com"
            serverPort = 10443
            password = "secret"
            idleSessionCheckInterval = "10s"
            idleSessionTimeout = "40s"
            minIdleSession = 3

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
            ech = true
            echConfig = "cfg-1\ncfg-2"
            echQueryServerName = "ech.example.com"
        }

        val outbound = buildSingBoxOutboundAnyTLSBean(bean)

        assertEquals(SingBoxOptions.TYPE_ANYTLS, outbound.type)
        assertEquals("edge.example.com", outbound.server)
        assertEquals(10443, outbound.server_port)
        assertEquals("secret", outbound.password)
        assertEquals("10s", outbound.idle_session_check_interval)
        assertEquals("40s", outbound.idle_session_timeout)
        assertEquals(3, outbound.min_idle_session)

        val tls = assertNotNull(outbound.tls)
        assertEquals(tls.enabled, true)
        assertEquals("sni.example.com", tls.server_name)
        assertEquals(tls.insecure, true)
        assertEquals(tls.disable_sni, true)
        assertTrue(tls.alpn == listOf("h2", "http/1.1"))
        assertTrue(tls.certificate == listOf("cert-a", "cert-b"))
        assertTrue(tls.client_certificate == listOf("client-cert"))
        assertTrue(tls.client_key == listOf("client-key-a", "client-key-b"))
        assertTrue(tls.certificate_public_key_sha256 == listOf("sha-a", "sha-b"))

        val utls = assertNotNull(tls.utls)
        assertEquals(utls.enabled, true)
        assertEquals(SingBoxOptions.FINGERPRINT_CHROME, utls.fingerprint)

        assertEquals(tls.fragment, true)
        assertEquals("200ms", tls.fragment_fallback_delay)
        assertEquals(tls.record_fragment, true)

        val ech = assertNotNull(tls.ech)
        assertEquals(ech.enabled, true)
        assertTrue(ech.config == listOf("cfg-1", "cfg-2"))
        assertEquals("ech.example.com", ech.query_server_name)
    }

    @Test
    fun `buildSingBoxOutboundAnyTLSBean should omit nullable fields for zero and blank values`() {
        val bean = AnyTLSBean().apply {
            serverAddress = "simple.example.com"
            serverPort = 443
            password = ""
            idleSessionCheckInterval = ""
            idleSessionTimeout = ""
            minIdleSession = 0
            serverName = ""
            alpn = ""
            certificates = ""
            clientCert = ""
            clientKey = ""
            certPublicKeySha256 = ""
            utlsFingerprint = ""
            tlsFragment = false
            tlsFragmentFallbackDelay = ""
            tlsRecordFragment = false
            ech = false
            echConfig = ""
            echQueryServerName = ""
        }

        val outbound = buildSingBoxOutboundAnyTLSBean(bean)

        assertEquals("", outbound.password)
        assertNull(outbound.idle_session_check_interval)
        assertNull(outbound.idle_session_timeout)
        assertNull(outbound.min_idle_session)

        val tls = assertNotNull(outbound.tls)
        assertNull(tls.server_name)
        assertNotEquals(tls.insecure, true)
        assertNotEquals(tls.disable_sni, true)
        assertNull(tls.alpn)
        assertNull(tls.certificate)
        assertNull(tls.client_certificate)
        assertNull(tls.client_key)
        assertNull(tls.certificate_public_key_sha256)
        assertNull(tls.utls)
        assertNotEquals(tls.fragment, true)
        assertNull(tls.fragment_fallback_delay)
        assertNotEquals(tls.record_fragment, true)
        assertNull(tls.ech)
    }

    @Test
    fun `parseAnyTLSOutbound should map base outbound and nested tls fields`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "node-a",
            "server" to "host.example.com",
            "server_port" to 9443L,
            "password" to "secret-pwd",
            "idle_session_check_interval" to "6s",
            "idle_session_timeout" to "12s",
            "min_idle_session" to "2",
            "tls" to mutableMapOf(
                "server_name" to "sni.example.com",
                "insecure" to true,
                "disable_sni" to true,
                "alpn" to listOf("h2", "http/1.1"),
                "certificate" to listOf("ca-1", "ca-2"),
                "client_certificate" to "cc-1",
                "client_key" to listOf("ck-1", "ck-2"),
                "certificate_public_key_sha256" to "sha-1",
                "utls" to mutableMapOf(
                    "enabled" to true,
                    "fingerprint" to SingBoxOptions.FINGERPRINT_FIREFOX,
                ),
                "fragment" to true,
                "fragment_fallback_delay" to "350ms",
                "record_fragment" to true,
                "ech" to mutableMapOf(
                    "enabled" to true,
                    "config" to listOf("ech-a", "ech-b"),
                    "query_server_name" to "ech-query.example.com",
                ),
            ),
        )

        val bean = parseAnyTLSOutbound(json)

        assertEquals("node-a", bean.name)
        assertEquals("host.example.com", bean.serverAddress)
        assertEquals(9443, bean.serverPort)
        assertEquals("secret-pwd", bean.password)
        assertEquals("6s", bean.idleSessionCheckInterval)
        assertEquals("12s", bean.idleSessionTimeout)
        assertEquals(2, bean.minIdleSession)

        assertEquals("sni.example.com", bean.serverName)
        assertTrue(bean.allowInsecure)
        assertTrue(bean.disableSNI)
        assertEquals("h2,http/1.1", bean.alpn)
        assertEquals("ca-1\nca-2", bean.certificates)
        assertEquals("cc-1", bean.clientCert)
        assertEquals("ck-1\nck-2", bean.clientKey)
        assertEquals("sha-1", bean.certPublicKeySha256)
        assertEquals(SingBoxOptions.FINGERPRINT_FIREFOX, bean.utlsFingerprint)
        assertTrue(bean.tlsFragment)
        assertEquals("350ms", bean.tlsFragmentFallbackDelay)
        assertTrue(bean.tlsRecordFragment)
        assertTrue(bean.ech)
        assertEquals("ech-a\nech-b", bean.echConfig)
        assertEquals("ech-query.example.com", bean.echQueryServerName)
    }
}
