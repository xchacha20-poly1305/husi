package fr.husi.fmt.v2ray

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.FmtTestConstant
import fr.husi.fmt.trojan.TrojanBean
import fr.husi.fmt.trojan.parseTrojan
import fr.husi.ktx.JSONMap
import fr.husi.ktx.b64EncodeOneLine
import fr.husi.ktx.asKxsMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class V2RayFmtTest {

    private val singBoxECHConfig = """
        -----BEGIN ECH CONFIGS-----
        AAj+DQAEAAAAAA==
        -----END ECH CONFIGS-----
    """.trimIndent()

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
    fun `parseV2Ray should parse v2rayN vmess with numeric port and aid`() {
        val shareJson = """
            {
              "port": 38949,
              "aid": 0,
              "id": "c7d456c6-fac6-41ae-988b-5fe46b951ec6",
              "ps": "test-node",
              "add": "13.11.16.140",
              "tls": "none",
              "v": "2",
              "net": "tcp",
              "host": "",
              "path": "",
              "type": "none"
            }
        """.trimIndent()
        val link = "vmess://${shareJson.b64EncodeOneLine()}"

        val bean = parseV2Ray(link)

        assertIs<VMessBean>(bean)
        assertEquals("13.11.16.140", bean.serverAddress)
        assertEquals(38949, bean.serverPort)
        assertEquals(0, bean.alterId)
        assertEquals("c7d456c6-fac6-41ae-988b-5fe46b951ec6", bean.uuid)
        assertEquals("tcp", bean.v2rayTransport)
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
    fun `toUriVMessVLESSTrojan should preserve ECH config in sing-box format`() {
        val source = VLESSBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "test-uuid"
            security = "tls"
            ech = true
            echConfig = singBoxECHConfig
        }

        val parsed = parseV2Ray(source.toUriVMessVLESSTrojan())

        assertTrue(parsed.ech)
        assertEquals(singBoxECHConfig, parsed.echConfig)
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
    fun `buildSingBoxOutboundStandardV2RayBean should map TLS spoof fields`() {
        val bean = VMessBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "test-uuid"
            encryption = "auto"
            security = "tls"
            sni = "sni.example.com"
            tlsSpoof = "spoof.example.com"
            tlsSpoofMethod = "wrong-checksum"
        }

        val outbound = buildSingBoxOutboundStandardV2RayBean(bean)

        val vmess = assertIs<SingBoxOptions.Outbound_VMessOptions>(outbound)
        val tls = assertNotNull(vmess.tls)
        assertEquals("spoof.example.com", tls.spoof)
        assertEquals("wrong-checksum", tls.spoof_method)
    }

    @Test
    fun `buildSingBoxOutboundStandardV2RayBean should omit blank TLS spoof fields`() {
        val bean = VMessBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "test-uuid"
            encryption = "auto"
            security = "tls"
            sni = "sni.example.com"
        }

        val outbound = buildSingBoxOutboundStandardV2RayBean(bean)

        val vmess = assertIs<SingBoxOptions.Outbound_VMessOptions>(outbound)
        val tls = assertNotNull(vmess.tls)
        assertNull(tls.spoof)
        assertNull(tls.spoof_method)
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
    fun `buildSingBoxOutboundStandardV2RayBean should preserve ws transport fields`() {
        val bean = VMessBean().apply {
            serverAddress = "example.com"
            serverPort = 10086
            uuid = "test-uuid"
            v2rayTransport = "ws"
            host = "host.example.com"
            path = "/ws"
            wsMaxEarlyData = 4096
            earlyDataHeaderName = "Sec-WebSocket-Protocol"
        }

        val outboundMap = buildSingBoxOutboundStandardV2RayBean(bean).asKxsMap()
        val transport = assertIs<Map<*, *>>(outboundMap["transport"])
        assertEquals("ws", transport["type"])
        assertEquals("/ws", transport["path"])
        assertEquals(4096L, transport["max_early_data"])
        assertEquals("Sec-WebSocket-Protocol", transport["early_data_header_name"])

        val headers = assertIs<Map<*, *>>(transport["headers"])
        assertEquals(listOf("host.example.com"), headers["Host"])
        assertNull(headers["host"])
    }

    @Test
    fun `buildSingBoxOutboundStandardV2RayBean should parse ws early data settings from path query`() {
        val bean = VMessBean().apply {
            serverAddress = "example.com"
            serverPort = 10086
            uuid = "test-uuid"
            v2rayTransport = "ws"
            path = "/ws?ed=2560&eh=Custom-Header"
        }

        val outboundMap = buildSingBoxOutboundStandardV2RayBean(bean).asKxsMap()
        val transport = assertIs<Map<*, *>>(outboundMap["transport"])
        assertEquals("/ws", transport["path"])
        assertEquals(2560L, transport["max_early_data"])
        assertEquals("Custom-Header", transport["early_data_header_name"])
    }

    @Test
    fun `buildSingBoxOutboundStandardV2RayBean should preserve blank ws path`() {
        val bean = VMessBean().apply {
            serverAddress = "example.com"
            serverPort = 10086
            uuid = "test-uuid"
            v2rayTransport = "ws"
            path = ""
        }

        val outboundMap = buildSingBoxOutboundStandardV2RayBean(bean).asKxsMap()
        val transport = assertIs<Map<*, *>>(outboundMap["transport"])
        assertEquals("", transport["path"])
        assertNull(transport["max_early_data"])
        assertNull(transport["early_data_header_name"])
    }

    @Test
    fun `buildSingBoxOutboundStandardV2RayBean should strip only ed from path with many query params`() {
        val bean = VMessBean().apply {
            serverAddress = "example.com"
            serverPort = 10086
            uuid = "test-uuid"
            v2rayTransport = "ws"
            path = "/ws?foo=1&ed=2560&bar=2&baz=hello%20world"
        }

        val outboundMap = buildSingBoxOutboundStandardV2RayBean(bean).asKxsMap()
        val transport = assertIs<Map<*, *>>(outboundMap["transport"])
        val path = assertIs<String>(transport["path"])
        assertEquals(2560L, transport["max_early_data"])
        assertNull(transport["early_data_header_name"])

        assertTrue(path.startsWith("/ws?"))
        assertFalse(path.contains("ed=2560"))
        assertTrue(path.contains("foo=1"))
        assertTrue(path.contains("bar=2"))
        assertTrue(path.contains("baz=hello"))
    }

    @Test
    fun `buildSingBoxOutboundStandardV2RayBean should let explicit wsMaxEarlyData override path query`() {
        val bean = VMessBean().apply {
            serverAddress = "example.com"
            serverPort = 10086
            uuid = "test-uuid"
            v2rayTransport = "ws"
            path = "/ws?ed=2048"
            wsMaxEarlyData = 4096
            earlyDataHeaderName = "Custom-Header"
        }

        val outboundMap = buildSingBoxOutboundStandardV2RayBean(bean).asKxsMap()
        val transport = assertIs<Map<*, *>>(outboundMap["transport"])
        assertEquals("/ws", transport["path"])
        assertEquals(4096L, transport["max_early_data"])
        assertEquals("Custom-Header", transport["early_data_header_name"])
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
