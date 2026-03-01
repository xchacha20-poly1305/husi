package fr.husi.fmt.v2ray

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.FmtTestConstant
import fr.husi.fmt.trojan.TrojanBean
import fr.husi.fmt.trojan.parseTrojan
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class V2RayFmtTest {

    @Test
    fun `parseV2Ray should parse vmess ducksoft url`() {
        val bean = parseV2Ray(FmtTestConstant.VMESS_DUCKSOFT_URL)

        assertIs<VMessBean>(bean)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(10086, bean.serverPort)
        assertEquals("uuid", bean.uuid)
        assertEquals("auto", bean.encryption)
        assertEquals("ws", bean.v2rayTransport)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("host.example.com", bean.host)
        assertEquals("/path", bean.path)
        assertEquals("test-vmess", bean.name)
        assertTrue(bean.isTLS)
    }

    @Test
    fun `parseV2Ray should parse vless url with flow`() {
        val bean = parseV2Ray(FmtTestConstant.VLESS_GRPC_URL)

        assertIs<VLESSBean>(bean)
        assertEquals("uuid", bean.uuid)
        assertEquals("grpc", bean.v2rayTransport)
        assertEquals("xtls-rprx-vision", bean.flow)
    }

    @Test
    fun `toUriVMessVLESSTrojan should preserve vmess fields`() {
        val source = VMessBean().apply {
            serverAddress = "example.com"
            serverPort = 10086
            uuid = "test-uuid"
            encryption = "auto"
            v2rayTransport = "ws"
            host = "host.example.com"
            path = "/path"
            security = "tls"
            sni = "sni.example.com"
            allowInsecure = true
            name = "vmess-node"
        }

        val parsed = parseV2Ray(source.toUriVMessVLESSTrojan())

        assertIs<VMessBean>(parsed)
        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.uuid, parsed.uuid)
        assertEquals(source.encryption, parsed.encryption)
        assertEquals(source.v2rayTransport, parsed.v2rayTransport)
        assertEquals(source.sni, parsed.sni)
        assertEquals(source.name, parsed.name)
    }

    @Test
    fun `toUriVMessVLESSTrojan should preserve vless fields with flow`() {
        val source = VLESSBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "test-uuid"
            flow = "xtls-rprx-vision"
            v2rayTransport = ""
            security = "tls"
            sni = "sni.example.com"
            name = "vless-node"
        }

        val parsed = parseV2Ray(source.toUriVMessVLESSTrojan())

        assertIs<VLESSBean>(parsed)
        assertEquals(source.uuid, parsed.uuid)
        assertEquals(source.flow, parsed.flow)
    }

    @Test
    fun `toUriVMessVLESSTrojan should preserve trojan fields`() {
        val source = TrojanBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            password = "trojan-pass"
            security = "tls"
            sni = "sni.example.com"
            name = "trojan-node"
        }

        val parsed = parseTrojan(source.toUriVMessVLESSTrojan())

        assertEquals(source.password, parsed.password)
        assertEquals(source.sni, parsed.sni)
    }

    @Test
    fun `buildSingBoxOutboundStandardV2RayBean should build vmess outbound`() {
        val bean = VMessBean().apply {
            serverAddress = "example.com"
            serverPort = 10086
            uuid = "test-uuid"
            encryption = "auto"
            security = "tls"
            sni = "sni.example.com"
            allowInsecure = true
        }

        val outbound = buildSingBoxOutboundStandardV2RayBean(bean)

        val vmess = assertIs<SingBoxOptions.Outbound_VMessOptions>(outbound)
        assertEquals(SingBoxOptions.TYPE_VMESS, vmess.type)
        assertEquals("example.com", vmess.server)
        assertEquals(10086, vmess.server_port)
        assertEquals("test-uuid", vmess.uuid)
        assertEquals("auto", vmess.security)

        val tls = assertNotNull(vmess.tls)
        assertEquals(tls.enabled, true)
        assertEquals("sni.example.com", tls.server_name)
        assertEquals(tls.insecure, true)
    }

    @Test
    fun `buildSingBoxOutboundStandardV2RayBean should build vless outbound with flow`() {
        val bean = VLESSBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "test-uuid"
            flow = "xtls-rprx-vision"
            security = "tls"
            sni = "sni.example.com"
        }

        val outbound = buildSingBoxOutboundStandardV2RayBean(bean)

        val vless = assertIs<SingBoxOptions.Outbound_VLESSOptions>(outbound)
        assertEquals(SingBoxOptions.TYPE_VLESS, vless.type)
        assertEquals("xtls-rprx-vision", vless.flow)
    }

    @Test
    fun `buildSingBoxOutboundStandardV2RayBean should build trojan outbound`() {
        val bean = TrojanBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            password = "secret"
            security = "tls"
            sni = "sni.example.com"
        }

        val outbound = buildSingBoxOutboundStandardV2RayBean(bean)

        val trojan = assertIs<SingBoxOptions.Outbound_TrojanOptions>(outbound)
        assertEquals(SingBoxOptions.TYPE_TROJAN, trojan.type)
        assertEquals("secret", trojan.password)
    }

    @Test
    fun `parseStandardV2RayOutbound should parse vmess`() {
        val json: JSONMap = mutableMapOf(
            "type" to "vmess",
            "tag" to "vmess-node",
            "server" to "example.com",
            "server_port" to 10086L,
            "uuid" to "test-uuid",
            "security" to "auto",
            "alter_id" to 0L,
            "tls" to mutableMapOf<String, Any?>(
                "enabled" to true,
                "server_name" to "sni.example.com",
                "insecure" to true,
            ),
        )

        val bean = parseStandardV2RayOutbound(json)

        assertIs<VMessBean>(bean)
        assertEquals("vmess-node", bean.name)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(10086, bean.serverPort)
        assertEquals("test-uuid", bean.uuid)
        assertTrue(bean.isTLS)
        assertEquals("sni.example.com", bean.sni)
        assertTrue(bean.allowInsecure)
    }

    @Test
    fun `parseStandardV2RayOutbound should parse trojan`() {
        val json: JSONMap = mutableMapOf(
            "type" to "trojan",
            "tag" to "trojan-node",
            "server" to "example.com",
            "server_port" to 443L,
            "password" to "secret",
            "tls" to mutableMapOf<String, Any?>(
                "enabled" to true,
                "server_name" to "sni.example.com",
            ),
        )

        val bean = parseStandardV2RayOutbound(json)

        assertIs<TrojanBean>(bean)
        assertEquals("trojan-node", bean.name)
        assertEquals("secret", bean.password)
        assertTrue(bean.isTLS)
        assertEquals("sni.example.com", bean.sni)
    }

    @Test
    fun `parseStandardV2RayOutbound should parse vless with flow`() {
        val json: JSONMap = mutableMapOf(
            "type" to "vless",
            "tag" to "vless-node",
            "server" to "example.com",
            "server_port" to 443L,
            "uuid" to "test-uuid",
            "flow" to "xtls-rprx-vision",
            "tls" to mutableMapOf<String, Any?>(
                "enabled" to true,
                "server_name" to "sni.example.com",
            ),
        )

        val bean = parseStandardV2RayOutbound(json)

        assertIs<VLESSBean>(bean)
        assertEquals("test-uuid", bean.uuid)
        assertEquals("xtls-rprx-vision", bean.flow)
    }
}
