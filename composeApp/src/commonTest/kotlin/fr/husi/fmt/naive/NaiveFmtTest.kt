package fr.husi.fmt.naive

import fr.husi.database.DataStore
import fr.husi.fmt.FmtTestConstant
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.JSONMap
import fr.husi.ktx.toJsonMapKxs
import fr.husi.test.HusiKoinTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NaiveFmtTest : HusiKoinTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    @Test
    fun `parseNaive should parse naive+https url`() {
        val bean = parseNaive(FmtTestConstant.NAIVE_HTTPS_URL)

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
        val bean = parseNaive(FmtTestConstant.NAIVE_QUIC_URL)

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
        assertEquals(true, tls.enabled)
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

        assertEquals(true, outbound.quic)
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
    fun `buildNaiveConfig should include timeout options`() {
        val bean = NaiveBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            tunnelTimeout = 600
            idleTimeout = 300
        }

        val config = bean.buildNaiveConfig(1080).toJsonMapKxs()

        assertEquals(600L, config["tunnel-timeout"])
        assertEquals(300L, config["idle-timeout"])
    }

    @Test
    fun `NaiveBean serialize round-trip should preserve timeout options`() {
        val source = NaiveBean().apply {
            tunnelTimeout = 600
            idleTimeout = 300
        }

        val restored = source.clone()

        assertEquals(600, restored.tunnelTimeout)
        assertEquals(300, restored.idleTimeout)
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
