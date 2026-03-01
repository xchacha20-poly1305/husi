package fr.husi.fmt.juicity

import fr.husi.fmt.FmtTestConstant
import fr.husi.fmt.SingBoxOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class JuicityFmtTest {

    @Test
    fun `parseJuicity should parse url with all fields`() {
        val bean = parseJuicity(FmtTestConstant.JUICITY_URL)

        assertEquals("example.com", bean.serverAddress)
        assertEquals(8443, bean.serverPort)
        assertEquals("uuid", bean.uuid)
        assertEquals("password", bean.password)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("sha256value", bean.pinSHA256)
    }

    @Test
    fun `parseJuicity should use default port 443 when not specified`() {
        val bean = parseJuicity(FmtTestConstant.JUICITY_DEFAULT_PORT_URL)

        assertEquals(443, bean.serverPort)
    }

    @Test
    fun `toUri should preserve serializable fields through parseJuicity`() {
        val source = JuicityBean().apply {
            serverAddress = "example.com"
            serverPort = 8443
            uuid = "test-uuid"
            password = "secret"
            sni = "sni.example.com"
            allowInsecure = true
            pinSHA256 = "sha256hash"
        }

        val parsed = parseJuicity(source.toUri())

        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.uuid, parsed.uuid)
        assertEquals(source.password, parsed.password)
        assertEquals(source.sni, parsed.sni)
        assertEquals(source.pinSHA256, parsed.pinSHA256)
    }

    @Test
    fun `buildSingBoxOutboundJuicityBean should map all fields`() {
        val bean = JuicityBean().apply {
            serverAddress = "example.com"
            serverPort = 8443
            uuid = "test-uuid"
            password = "secret"
            sni = "sni.example.com"
            allowInsecure = true
            pinSHA256 = "sha256hash"
        }

        val outbound = buildSingBoxOutboundJuicityBean(bean)

        assertEquals(SingBoxOptions.TYPE_JUICITY, outbound.type)
        assertEquals("example.com", outbound.server)
        assertEquals(8443, outbound.server_port)
        assertEquals("test-uuid", outbound.uuid)
        assertEquals("secret", outbound.password)
        assertEquals("sha256hash", outbound.pin_cert_sha256)

        val tls = assertNotNull(outbound.tls)
        assertEquals(tls.enabled, true)
        assertEquals("sni.example.com", tls.server_name)
        assertEquals(tls.insecure, true)
    }

    @Test
    fun `buildSingBoxOutboundJuicityBean should omit pin_cert_sha256 when blank`() {
        val bean = JuicityBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "uuid"
            password = "pass"
            pinSHA256 = ""
        }

        val outbound = buildSingBoxOutboundJuicityBean(bean)

        assertNull(outbound.pin_cert_sha256)
    }
}
