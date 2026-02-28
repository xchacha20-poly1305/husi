package fr.husi.fmt.shadowtls

import fr.husi.fmt.SingBoxOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ShadowTLSFmtTest {

    @Test
    fun `buildSingBoxOutboundShadowTLSBean should map type server port version and password`() {
        val bean = ShadowTLSBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            protocolVersion = 3
            password = "secret"
            security = "tls"
        }

        val outbound = buildSingBoxOutboundShadowTLSBean(bean)

        assertEquals(SingBoxOptions.TYPE_SHADOWTLS, outbound.type)
        assertEquals("example.com", outbound.server)
        assertEquals(443, outbound.server_port)
        assertEquals(3, outbound.version)
        assertEquals("secret", outbound.password)
    }

    @Test
    fun `buildSingBoxOutboundShadowTLSBean should include tls options when security is tls`() {
        val bean = ShadowTLSBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            protocolVersion = 3
            password = "secret"
            security = "tls"
            sni = "sni.example.com"
            allowInsecure = true
            utlsFingerprint = SingBoxOptions.FINGERPRINT_CHROME
        }

        val outbound = buildSingBoxOutboundShadowTLSBean(bean)

        val tls = assertNotNull(outbound.tls)
        assertEquals(tls.enabled, true)
        assertEquals("sni.example.com", tls.server_name)
        assertEquals(tls.insecure, true)
        val utls = assertNotNull(tls.utls)
        assertEquals(SingBoxOptions.FINGERPRINT_CHROME, utls.fingerprint)
    }
}
