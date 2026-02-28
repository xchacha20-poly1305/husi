package fr.husi.fmt.trusttunnel

import fr.husi.fmt.SingBoxOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustTunnelFmtTest {

    @Test
    fun `buildSingBoxOutboundTrustTunnelBean should map all TLS and connection fields`() {
        val bean = TrustTunnelBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            username = "user"
            password = "pass"
            healthCheck = true
            quic = true
            quicCongestionControl = "bbr"
            serverName = "sni.example.com"
            allowInsecure = true
            alpn = "h2,http/1.1"
            certificates = "cert-a\ncert-b"
            certPublicKeySha256 = "sha-a\nsha-b"
            clientCert = "client-cert"
            clientKey = "client-key"
            utlsFingerprint = SingBoxOptions.FINGERPRINT_CHROME
            tlsFragment = true
            tlsFragmentFallbackDelay = "200ms"
            ech = true
            echConfig = "cfg-1\ncfg-2"
            echQueryServerName = "ech.example.com"
        }

        val outbound = buildSingBoxOutboundTrustTunnelBean(bean)

        assertEquals(SingBoxOptions.TYPE_TRUST_TUNNEL, outbound.type)
        assertEquals("example.com", outbound.server)
        assertEquals(443, outbound.server_port)
        assertEquals("user", outbound.username)
        assertEquals("pass", outbound.password)
        assertTrue(outbound.health_check == true)
        assertTrue(outbound.quic == true)
        assertEquals("bbr", outbound.quic_congestion_control)

        val tls = assertNotNull(outbound.tls)
        assertTrue(tls.enabled == true)
        assertEquals("sni.example.com", tls.server_name)
        assertTrue(tls.insecure == true)
        assertEquals(listOf("h2", "http/1.1"), tls.alpn?.toList())
        assertEquals(listOf("cert-a", "cert-b"), tls.certificate?.toList())
        assertEquals(listOf("sha-a", "sha-b"), tls.certificate_public_key_sha256?.toList())
        assertEquals(listOf("client-cert"), tls.client_certificate?.toList())
        assertEquals(listOf("client-key"), tls.client_key?.toList())

        val utls = assertNotNull(tls.utls)
        assertEquals(SingBoxOptions.FINGERPRINT_CHROME, utls.fingerprint)

        assertTrue(tls.fragment == true)
        assertEquals("200ms", tls.fragment_fallback_delay)

        val echOpts = assertNotNull(tls.ech)
        assertTrue(echOpts.enabled == true)
        assertEquals(listOf("cfg-1", "cfg-2"), echOpts.config?.toList())
        assertEquals("ech.example.com", echOpts.query_server_name)
    }

    @Test
    fun `buildSingBoxOutboundTrustTunnelBean should omit optional fields for default values`() {
        val bean = TrustTunnelBean().apply {
            serverAddress = "example.com"
            serverPort = 443
        }

        val outbound = buildSingBoxOutboundTrustTunnelBean(bean)

        val tls = assertNotNull(outbound.tls)
        assertNull(tls.server_name)
        assertNull(tls.alpn)
        assertNull(tls.certificate)
        assertNull(tls.certificate_public_key_sha256)
        assertNull(tls.utls)
        assertNull(tls.ech)
        assertNull(tls.fragment_fallback_delay)
    }

    @Test
    fun `buildSingBoxOutboundTrustTunnelBean should set record_fragment when only tlsRecordFragment is true`() {
        val bean = TrustTunnelBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            tlsFragment = false
            tlsRecordFragment = true
        }

        val outbound = buildSingBoxOutboundTrustTunnelBean(bean)

        val tls = assertNotNull(outbound.tls)
        assertTrue(tls.record_fragment == true)
        assertNull(tls.fragment_fallback_delay)
    }
}
