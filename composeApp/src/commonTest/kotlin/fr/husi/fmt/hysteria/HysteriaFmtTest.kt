package fr.husi.fmt.hysteria

import fr.husi.database.DataStore
import fr.husi.fmt.FmtTestConstant
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.JSONMap
import fr.husi.test.HusiKoinTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HysteriaFmtTest : HusiKoinTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    @Test
    fun `HopPort from should return Single for a plain port`() {
        val result = assertIs<HopPort.Single>(HopPort.from("9080"))

        assertEquals(9080, result.port)
    }

    @Test
    fun `HopPort from should return Single with 443 when input is blank`() {
        val result = assertIs<HopPort.Single>(HopPort.from(""))

        assertEquals(443, result.port)
    }

    @Test
    fun `HopPort from should return Ports for a range`() {
        val result = HopPort.from("1000-2000")

        assertTrue(result is HopPort.Ports)
    }

    @Test
    fun `HopPort Ports singStyle should convert hysteria range to sing-box colon style`() {
        val ports = HopPort.Ports(listOf("1000-2000", "3000"))

        val singStyle = ports.singStyle()

        assertEquals("1000:2000", singStyle[0])
        assertEquals("3000:3000", singStyle[1])
    }

    @Test
    fun `HopPort Ports hyStyle should convert sing-box range to hysteria dash style`() {
        val ports = HopPort.Ports(listOf("1000:2000", "3000"))

        val hyStyle = ports.hyStyle()

        assertEquals("1000-2000", hyStyle[0])
    }

    @Test
    fun `parseHysteria1 should parse url with all fields`() {
        val bean = parseHysteria1(FmtTestConstant.HYSTERIA1_URL)

        assertEquals(HysteriaBean.PROTOCOL_VERSION_1, bean.protocolVersion)
        assertEquals("example.com", bean.serverAddress)
        assertEquals("9080", bean.serverPorts)
        assertEquals(HysteriaBean.TYPE_STRING, bean.authPayloadType)
        assertEquals("secret", bean.authPayload)
        assertEquals("sni.example.com", bean.sni)
        assertTrue(bean.allowInsecure)
        assertEquals("hysteria", bean.alpn)
        assertEquals("test", bean.name)
    }

    @Test
    fun `parseHysteria1 should detect faketcp protocol`() {
        val bean = parseHysteria1(FmtTestConstant.HYSTERIA1_FAKETCP_URL)

        assertEquals(HysteriaBean.PROTOCOL_FAKETCP, bean.protocol)
    }

    @Test
    fun `parseHysteria2 should parse url with password auth`() {
        val bean = parseHysteria2(FmtTestConstant.HYSTERIA2_URL)

        assertEquals(HysteriaBean.PROTOCOL_VERSION_2, bean.protocolVersion)
        assertEquals("example.com", bean.serverAddress)
        assertEquals("9443", bean.serverPorts)
        assertEquals("secret", bean.authPayload)
        assertEquals("sni.example.com", bean.sni)
        assertTrue(bean.allowInsecure)
    }

    @Test
    fun `parseHysteria2 should combine user and password when both present`() {
        val bean = parseHysteria2(FmtTestConstant.HYSTERIA2_USER_PASS_URL)

        assertEquals("user:pass", bean.authPayload)
    }

    @Test
    fun `toUri should preserve fields for hy1 through parseHysteria1`() {
        val source = HysteriaBean().apply {
            protocolVersion = HysteriaBean.PROTOCOL_VERSION_1
            serverAddress = "example.com"
            serverPorts = "9080"
            authPayload = "secret"
            authPayloadType = HysteriaBean.TYPE_STRING
            sni = "sni.example.com"
            allowInsecure = true
            name = "hy1-node"
        }

        val parsed = parseHysteria1(source.toUri())

        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPorts, parsed.serverPorts)
        assertEquals(source.authPayload, parsed.authPayload)
        assertEquals(source.sni, parsed.sni)
        assertEquals(source.allowInsecure, parsed.allowInsecure)
    }

    @Test
    fun `toUri should preserve fields for hy2 through parseHysteria2`() {
        val source = HysteriaBean().apply {
            protocolVersion = HysteriaBean.PROTOCOL_VERSION_2
            serverAddress = "example.com"
            serverPorts = "9443"
            authPayload = "secret"
            sni = "sni.example.com"
            allowInsecure = true
        }

        val parsed = parseHysteria2(source.toUri())

        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPorts, parsed.serverPorts)
        assertEquals(source.authPayload, parsed.authPayload)
        assertEquals(source.sni, parsed.sni)
        assertEquals(source.allowInsecure, parsed.allowInsecure)
    }

    @Test
    fun `parseHysteria1Outbound should map tag server auth and tls`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "hy1-node",
            "server" to "example.com",
            "server_port" to 9080L,
            "auth_str" to "secret",
            "tls" to mutableMapOf<String, Any?>(
                "server_name" to "sni.example.com",
                "insecure" to true,
                "alpn" to listOf("hysteria"),
                "certificate" to listOf("cert-1", "cert-2"),
                "certificate_public_key_sha256" to "sha-1",
            ),
        )

        val bean = parseHysteria1Outbound(json)

        assertEquals("hy1-node", bean.name)
        assertEquals("example.com", bean.serverAddress)
        assertEquals("9080", bean.serverPorts)
        assertEquals(HysteriaBean.TYPE_STRING, bean.authPayloadType)
        assertEquals("secret", bean.authPayload)
        assertEquals("sni.example.com", bean.sni)
        assertTrue(bean.allowInsecure)
        assertEquals("hysteria", bean.alpn)
        assertEquals("cert-1\ncert-2", bean.certificates)
        assertEquals("sha-1", bean.certPublicKeySha256)
    }

    @Test
    fun `parseHysteria2Outbound should map server password obfs and tls`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "hy2-node",
            "server" to "example.com",
            "server_port" to 9443L,
            "password" to "secret",
            "obfs" to mutableMapOf<String, Any?>(
                "type" to "salamander",
                "password" to "obfs-secret",
            ),
            "tls" to mutableMapOf<String, Any?>(
                "server_name" to "sni.example.com",
                "insecure" to true,
            ),
        )

        val bean = parseHysteria2Outbound(json)

        assertEquals("hy2-node", bean.name)
        assertEquals("example.com", bean.serverAddress)
        assertEquals("9443", bean.serverPorts)
        assertEquals("secret", bean.authPayload)
        assertEquals("obfs-secret", bean.obfuscation)
        assertEquals("sni.example.com", bean.sni)
        assertTrue(bean.allowInsecure)
    }

    @Test
    fun `buildSingBoxOutboundHysteriaBean hy1 should emit new QUIC field names and drop legacy`() {
        val bean = HysteriaBean().apply {
            protocolVersion = HysteriaBean.PROTOCOL_VERSION_1
            serverAddress = "example.com"
            serverPorts = "9080"
            streamReceiveWindow = 65536
            connectionReceiveWindow = 131072
            disableMtuDiscovery = true
            idleTimeout = "30s"
            keepAlivePeriod = "15s"
            maxConcurrentStreams = 128
            initialPacketSize = 1200
        }

        val outbound = assertIs<SingBoxOptions.Outbound_HysteriaOptions>(
            buildSingBoxOutboundHysteriaBean(bean),
        )

        assertEquals(65536, outbound.stream_receive_window)
        assertEquals(131072, outbound.connection_receive_window)
        assertEquals(true, outbound.disable_path_mtu_discovery)
        assertEquals("30s", outbound.idle_timeout)
        assertEquals("15s", outbound.keep_alive_period)
        assertEquals(128, outbound.max_concurrent_streams)
        assertEquals(1200, outbound.initial_packet_size)

        assertNull(outbound.recv_window_conn)
        assertNull(outbound.recv_window)
        assertNull(outbound.disable_mtu_discovery)
    }

    @Test
    fun `buildSingBoxOutboundHysteriaBean hy1 should leave QUIC fields null when defaults`() {
        val bean = HysteriaBean().apply {
            protocolVersion = HysteriaBean.PROTOCOL_VERSION_1
            serverAddress = "example.com"
            serverPorts = "9080"
        }

        val outbound = assertIs<SingBoxOptions.Outbound_HysteriaOptions>(
            buildSingBoxOutboundHysteriaBean(bean),
        )

        assertNull(outbound.stream_receive_window)
        assertNull(outbound.connection_receive_window)
        assertNull(outbound.disable_path_mtu_discovery)
        assertNull(outbound.idle_timeout)
        assertNull(outbound.keep_alive_period)
        assertNull(outbound.max_concurrent_streams)
        assertNull(outbound.initial_packet_size)
    }

    @Test
    fun `buildSingBoxOutboundHysteriaBean hy2 should emit QUIC fields`() {
        val bean = HysteriaBean().apply {
            protocolVersion = HysteriaBean.PROTOCOL_VERSION_2
            serverAddress = "example.com"
            serverPorts = "9443"
            authPayload = "secret"
            streamReceiveWindow = 65536
            connectionReceiveWindow = 131072
            disableMtuDiscovery = true
            idleTimeout = "30s"
            keepAlivePeriod = "15s"
            maxConcurrentStreams = 128
            initialPacketSize = 1200
        }

        val outbound = assertIs<SingBoxOptions.Outbound_Hysteria2Options>(
            buildSingBoxOutboundHysteriaBean(bean),
        )

        assertEquals(65536, outbound.stream_receive_window)
        assertEquals(131072, outbound.connection_receive_window)
        assertEquals(true, outbound.disable_path_mtu_discovery)
        assertEquals("30s", outbound.idle_timeout)
        assertEquals("15s", outbound.keep_alive_period)
        assertEquals(128, outbound.max_concurrent_streams)
        assertEquals(1200, outbound.initial_packet_size)
    }

    @Test
    fun `HysteriaBean serialize round-trip should preserve QUIC fields`() {
        val source = HysteriaBean().apply {
            protocolVersion = HysteriaBean.PROTOCOL_VERSION_2
            serverAddress = "example.com"
            serverPorts = "9443"
            idleTimeout = "30s"
            keepAlivePeriod = "15s"
            maxConcurrentStreams = 128
            initialPacketSize = 1200
        }

        val restored = source.clone()

        assertEquals("30s", restored.idleTimeout)
        assertEquals("15s", restored.keepAlivePeriod)
        assertEquals(128, restored.maxConcurrentStreams)
        assertEquals(1200, restored.initialPacketSize)
    }

    @Test
    fun `applyFeatureSettings should copy QUIC fields`() {
        val source = HysteriaBean().apply {
            idleTimeout = "30s"
            keepAlivePeriod = "15s"
            maxConcurrentStreams = 128
            initialPacketSize = 1200
        }
        val target = HysteriaBean()

        source.applyFeatureSettings(target)

        assertEquals("30s", target.idleTimeout)
        assertEquals("15s", target.keepAlivePeriod)
        assertEquals(128, target.maxConcurrentStreams)
        assertEquals(1200, target.initialPacketSize)
    }
}
