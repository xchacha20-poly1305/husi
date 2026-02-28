package fr.husi.fmt.tuic

import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TuicFmtTest {

    @Test
    fun `parseTuic should parse url with all fields`() {
        val bean = parseTuic(
            "tuic://uuid:token@example.com:9443?sni=sni.example.com&congestion_control=bbr&udp_relay_mode=native&alpn=h3&allow_insecure=1&disable_sni=1#test-node",
        )

        assertEquals("example.com", bean.serverAddress)
        assertEquals(9443, bean.serverPort)
        assertEquals("uuid", bean.uuid)
        assertEquals("token", bean.token)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("bbr", bean.congestionController)
        assertEquals("native", bean.udpRelayMode)
        assertEquals("h3", bean.alpn)
        assertTrue(bean.allowInsecure)
        assertTrue(bean.disableSNI)
        assertEquals("test-node", bean.name)
    }

    @Test
    fun `parseTuic should use default port 443 when not specified`() {
        val bean = parseTuic("tuic://uuid:token@example.com")

        assertEquals(443, bean.serverPort)
    }

    @Test
    fun `toUri should preserve serializable fields through parseTuic`() {
        val source = TuicBean().apply {
            serverAddress = "example.com"
            serverPort = 9443
            uuid = "test-uuid"
            token = "token123"
            sni = "sni.example.com"
            congestionController = "cubic"
            udpRelayMode = "native"
            alpn = "h3"
            allowInsecure = true
            name = "test-node"
        }

        val parsed = parseTuic(source.toUri())

        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.uuid, parsed.uuid)
        assertEquals(source.token, parsed.token)
        assertEquals(source.sni, parsed.sni)
        assertEquals(source.congestionController, parsed.congestionController)
        assertEquals(source.udpRelayMode, parsed.udpRelayMode)
        assertEquals(source.allowInsecure, parsed.allowInsecure)
    }

    @Test
    fun `buildSingBoxOutboundTuicBean should map all fields`() {
        val bean = TuicBean().apply {
            serverAddress = "example.com"
            serverPort = 9443
            uuid = "test-uuid"
            token = "token123"
            congestionController = "bbr"
            udpRelayMode = "native"
            zeroRTT = true
            sni = "sni.example.com"
            allowInsecure = true
            alpn = "h3"
            certificates = "cert-1\ncert-2"
            certPublicKeySha256 = "sha-1\nsha-2"
            clientCert = "client-cert"
            clientKey = "client-key"
            ech = true
            echConfig = "ech-config"
            echQueryServerName = "ech.example.com"
        }

        val outbound = buildSingBoxOutboundTuicBean(bean)

        assertEquals(SingBoxOptions.TYPE_TUIC, outbound.type)
        assertEquals("example.com", outbound.server)
        assertEquals(9443, outbound.server_port)
        assertEquals("test-uuid", outbound.uuid)
        assertEquals("token123", outbound.password)
        assertEquals("bbr", outbound.congestion_control)
        assertEquals("native", outbound.udp_relay_mode)
        assertEquals(outbound.zero_rtt_handshake, true)

        val tls = assertNotNull(outbound.tls)
        assertEquals(tls.enabled, true)
        assertEquals("sni.example.com", tls.server_name)
        assertEquals(tls.insecure, true)
        assertEquals(listOf("h3"), tls.alpn?.toList())
        assertEquals(listOf("cert-1", "cert-2"), tls.certificate?.toList())
        assertEquals(listOf("sha-1", "sha-2"), tls.certificate_public_key_sha256?.toList())
        assertEquals(listOf("client-cert"), tls.client_certificate?.toList())
        assertEquals(listOf("client-key"), tls.client_key?.toList())

        val echOpts = assertNotNull(tls.ech)
        assertEquals(echOpts.enabled, true)
        assertEquals(listOf("ech-config"), echOpts.config?.toList())
        assertEquals("ech.example.com", echOpts.query_server_name)
    }

    @Test
    fun `parseTuicOutbound should map uuid token congestion and tls`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "tuic-node",
            "server" to "example.com",
            "server_port" to 9443L,
            "uuid" to "test-uuid",
            "password" to "token123",
            "congestion_control" to "bbr",
            "udp_relay_mode" to "native",
            "zero_rtt_handshake" to "true",
            "tls" to mutableMapOf<String, Any?>(
                "server_name" to "sni.example.com",
                "insecure" to true,
                "alpn" to listOf("h3"),
                "certificate" to listOf("cert-1", "cert-2"),
                "certificate_public_key_sha256" to "sha-1",
                "client_certificate" to "client-cert",
                "client_key" to listOf("ck-1", "ck-2"),
                "ech" to mutableMapOf<String, Any?>(
                    "enabled" to true,
                    "config" to listOf("ech-cfg"),
                    "query_server_name" to "ech.example.com",
                ),
            ),
        )

        val bean = parseTuicOutbound(json)

        assertEquals("tuic-node", bean.name)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(9443, bean.serverPort)
        assertEquals("test-uuid", bean.uuid)
        assertEquals("token123", bean.token)
        assertEquals("bbr", bean.congestionController)
        assertEquals("native", bean.udpRelayMode)
        assertTrue(bean.zeroRTT)
        assertEquals("sni.example.com", bean.sni)
        assertTrue(bean.allowInsecure)
        assertEquals("h3", bean.alpn)
        assertEquals("cert-1\ncert-2", bean.certificates)
        assertEquals("sha-1", bean.certPublicKeySha256)
        assertEquals("client-cert", bean.clientCert)
        assertEquals("ck-1\nck-2", bean.clientKey)
        assertTrue(bean.ech)
        assertEquals("ech-cfg", bean.echConfig)
        assertEquals("ech.example.com", bean.echQueryServerName)
    }
}
