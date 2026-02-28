package fr.husi.fmt.naive

import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NaiveFmtTest {

    @Test
    fun `parseNaive should parse naive+https url`() {
        val bean = parseNaive(
            "naive+https://user:pass@example.com:443?sni=sni.example.com#test-node",
        )

        assertEquals("https", bean.proto)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("user", bean.username)
        assertEquals("pass", bean.password)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("test-node", bean.name)
    }

    @Test
    fun `parseNaive should parse naive+quic url`() {
        val bean = parseNaive("naive+quic://user:pass@example.com:443?insecure-concurrency=3")

        assertEquals("quic", bean.proto)
        assertEquals(3, bean.insecureConcurrency)
    }

    @Test
    fun `buildSingBoxOutboundNaiveBean should map https proto fields`() {
        val bean = NaiveBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            username = "user"
            password = "pass"
            proto = NaiveBean.PROTO_HTTPS
            sni = "sni.example.com"
            insecureConcurrency = 3
        }

        val outbound = buildSingBoxOutboundNaiveBean(bean)

        assertEquals(SingBoxOptions.TYPE_NAIVE, outbound.type)
        assertEquals("example.com", outbound.server)
        assertEquals(443, outbound.server_port)
        assertEquals("user", outbound.username)
        assertEquals("pass", outbound.password)
        assertEquals(3, outbound.insecure_concurrency)
        assertNull(outbound.quic)

        val tls = assertNotNull(outbound.tls)
        assertEquals(tls.enabled, true)
        assertEquals("sni.example.com", tls.server_name)
    }

    @Test
    fun `buildSingBoxOutboundNaiveBean should set quic fields for quic proto`() {
        val bean = NaiveBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            username = "user"
            password = "pass"
            proto = NaiveBean.PROTO_QUIC
            quicCongestionControl = "bbr"
        }

        val outbound = buildSingBoxOutboundNaiveBean(bean)

        assertEquals(outbound.quic, true)
        assertEquals("bbr", outbound.quic_congestion_control)
    }

    @Test
    fun `buildSingBoxOutboundNaiveBean should omit insecure_concurrency when zero`() {
        val bean = NaiveBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            proto = NaiveBean.PROTO_HTTPS
            insecureConcurrency = 0
        }

        val outbound = buildSingBoxOutboundNaiveBean(bean)

        assertNull(outbound.insecure_concurrency)
    }

    @Test
    fun `parseNaiveOutbound should map tag server credentials and sni`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "naive-node",
            "server" to "example.com",
            "server_port" to 443L,
            "username" to "user",
            "password" to "pass",
            "tls" to mutableMapOf<String, Any?>(
                "server_name" to "sni.example.com",
            ),
        )

        val bean = parseNaiveOutbound(json)

        assertEquals("naive-node", bean.name)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("user", bean.username)
        assertEquals("pass", bean.password)
        assertEquals("sni.example.com", bean.sni)
    }

    @Test
    fun `parseNaiveOutbound should set proto to quic when quic field is true`() {
        val json: JSONMap = mutableMapOf(
            "server" to "example.com",
            "server_port" to 443L,
            "quic" to "true",
            "quic_congestion_control" to "bbr",
        )

        val bean = parseNaiveOutbound(json)

        assertEquals(NaiveBean.PROTO_QUIC, bean.proto)
        assertEquals("bbr", bean.quicCongestionControl)
    }
}
