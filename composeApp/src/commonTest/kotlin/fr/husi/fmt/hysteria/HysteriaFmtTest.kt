package fr.husi.fmt.hysteria

import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class HysteriaFmtTest {

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
        val bean = parseHysteria1(
            "hysteria://example.com:9080?auth=secret&peer=sni.example.com&insecure=1&alpn=hysteria#test",
        )

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
        val bean = parseHysteria1("hysteria://example.com:9080?auth=abc&protocol=faketcp")

        assertEquals(HysteriaBean.PROTOCOL_FAKETCP, bean.protocol)
    }

    @Test
    fun `parseHysteria2 should parse url with password auth`() {
        val bean = parseHysteria2(
            "hysteria2://secret@example.com:9443?sni=sni.example.com&insecure=1#test",
        )

        assertEquals(HysteriaBean.PROTOCOL_VERSION_2, bean.protocolVersion)
        assertEquals("example.com", bean.serverAddress)
        assertEquals("9443", bean.serverPorts)
        assertEquals("secret", bean.authPayload)
        assertEquals("sni.example.com", bean.sni)
        assertTrue(bean.allowInsecure)
        assertEquals("test", bean.name)
    }

    @Test
    fun `parseHysteria2 should combine user and password when both present`() {
        val bean = parseHysteria2("hysteria2://user:pass@example.com:9443")

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
}
