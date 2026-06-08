package fr.husi.fmt.shadowquic

import fr.husi.database.DataStore
import fr.husi.database.ProxyEntity
import fr.husi.fmt.KryoConverters
import fr.husi.ktx.getObject
import fr.husi.ktx.toJsonMapKxs
import fr.husi.test.HusiKoinTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShadowQUICFmtTest : HusiKoinTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    @Test
    fun `parseShadowQUIC should parse sq share link fields`() {
        val bean = parseShadowQUIC(
            "sq://myuser:mypass@example.com:8443?sni=cdn.com&udp_mode=datagram" +
                "&zero_rtt=true&mtu=1400&alpn=h3,h2,http%2F1.1#my%20tag%20with%20spaces",
        )

        assertEquals(ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC, bean.subProtocol)
        assertEquals("myuser", bean.username)
        assertEquals("mypass", bean.password)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(8443, bean.serverPort)
        assertEquals("cdn.com", bean.sni)
        assertFalse(bean.udpOverStream)
        assertTrue(bean.zeroRTT)
        assertEquals(1400, bean.initialMTU)
        assertEquals(1400, bean.minimumMTU)
        assertEquals("h3,h2,http/1.1", bean.alpn)
        assertEquals("my tag with spaces", bean.name)
    }

    @Test
    fun `parseShadowQUIC should accept shadowquic scheme and default port mtu udp mode`() {
        val bean = parseShadowQUIC("shadowquic://user:pass@example.com?sni=cdn.com#node-02")

        assertEquals("example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals(1280, bean.initialMTU)
        assertEquals(1280, bean.minimumMTU)
        assertTrue(bean.udpOverStream)
        assertFalse(bean.zeroRTT)
        assertEquals("node-02", bean.name)
    }

    @Test
    fun `parseShadowQUIC should enable zero rtt only for non blank query value`() {
        val emptyValue = parseShadowQUIC("sq://user:pass@example.com?sni=cdn.com&zero_rtt=")
        val flagOnly = parseShadowQUIC("sq://user:pass@example.com?sni=cdn.com&zero_rtt")
        val nonBlankValue = parseShadowQUIC("sq://user:pass@example.com?sni=cdn.com&zero_rtt=false")

        assertFalse(emptyValue.zeroRTT)
        assertFalse(flagOnly.zeroRTT)
        assertTrue(nonBlankValue.zeroRTT)
    }

    @Test
    fun `parseShadowQUIC should reject missing required fields`() {
        assertFailsWith<IllegalStateException> {
            parseShadowQUIC("sq://user:pass@example.com")
        }
        assertFailsWith<IllegalArgumentException> {
            parseShadowQUIC("sq://:pass@example.com?sni=cdn.com")
        }
        assertFailsWith<IllegalArgumentException> {
            parseShadowQUIC("sq://user:@example.com?sni=cdn.com")
        }
    }

    @Test
    fun `toUri should export sq share link and preserve parsed fields`() {
        val source = ShadowQUICBean().apply {
            subProtocol = ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC
            serverAddress = "example.com"
            serverPort = 8443
            username = "myuser"
            password = "mypass"
            sni = "cdn.com"
            udpOverStream = false
            zeroRTT = true
            initialMTU = 1400
            minimumMTU = 1400
            alpn = "h3,h2,http/1.1"
            name = "my tag with spaces"
        }

        val uri = source.toUri()
        val parsed = parseShadowQUIC(uri)

        assertTrue(uri.startsWith("sq://"))
        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.username, parsed.username)
        assertEquals(source.password, parsed.password)
        assertEquals(source.sni, parsed.sni)
        assertEquals(source.udpOverStream, parsed.udpOverStream)
        assertEquals(source.zeroRTT, parsed.zeroRTT)
        assertEquals(source.initialMTU, parsed.initialMTU)
        assertEquals(source.minimumMTU, parsed.minimumMTU)
        assertEquals(source.alpn, parsed.alpn)
        assertEquals(source.name, parsed.name)
    }

    @Test
    fun `standard share link should be unavailable for sunnyquic`() {
        val shadowQUIC = ProxyEntity().putBean(
            ShadowQUICBean().apply {
                subProtocol = ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC
            },
        )
        val sunnyQUIC = ProxyEntity().putBean(
            ShadowQUICBean().apply {
                subProtocol = ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC
            },
        )

        assertTrue(shadowQUIC.haveStandardLink())
        assertFalse(sunnyQUIC.haveStandardLink())
    }

    @Test
    fun `buildShadowQUICConfig should produce json with inbound port and outbound credentials`() {
        val bean = ShadowQUICBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            username = "user"
            password = "pass"
            sni = "sni.example.com"
            congestionControl = "bbr"
            mtuDiscovery = true
            blackholeDetection = true
            subProtocol = ShadowQUICBean.SUB_PROTOCOL_SHADOW_QUIC
        }
        bean.initializeDefaultValues()

        val config = bean.buildShadowQUICConfig(port = 2080, shouldProtect = false, logLevel = 0)
        val root = config.toJsonMapKxs()
        val inbound = root.getObject("inbound")!!
        val outbound = root.getObject("outbound")!!

        assertEquals("socks", inbound["type"])
        assertEquals("127.0.0.1:2080", inbound["bind-addr"])
        assertEquals("shadowquic", outbound["type"])
        assertEquals("example.com:443", outbound["addr"])
        assertEquals("user", outbound["username"])
        assertEquals("pass", outbound["password"])
        assertEquals("sni.example.com", outbound["server-name"])
        assertEquals("bbr", outbound["congestion-control"])
        assertEquals(true, outbound["blackhole-detection"])
        assertEquals("error", root["log-level"])
    }

    @Test
    fun `buildShadowQUICConfig should use sunnyquic type for sunny_quic subprotocol`() {
        val bean = ShadowQUICBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            username = "user"
            password = "pass"
            subProtocol = ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC
        }
        bean.initializeDefaultValues()

        val config = bean.buildShadowQUICConfig(port = 2080, shouldProtect = false, logLevel = 0)
        val root = config.toJsonMapKxs()
        val outbound = root.getObject("outbound")!!

        assertEquals("sunnyquic", outbound["type"])
        assertEquals("example.com:443", outbound["addr"])
        assertEquals("user", outbound["username"])
        assertEquals("pass", outbound["password"])
    }

    @Test
    fun `buildShadowQUICConfig should emit brutal bandwidth in decimal bps`() {
        DataStore.uploadSpeed = 100

        val bean = ShadowQUICBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            username = "user"
            password = "pass"
            congestionControl = ShadowQUICBean.CONGESTION_CONTROL_BRUTAL
        }
        bean.initializeDefaultValues()

        val config = bean.buildShadowQUICConfig(port = 2080, shouldProtect = false, logLevel = 0)
        val outbound = config.toJsonMapKxs().getObject("outbound")!!
        val congestionControl = outbound.getObject("congestion-control")!!
        val brutal = congestionControl.getObject("brutal")!!

        assertEquals(100_000_000L, brutal["bandwidth"])
    }

    @Test
    fun `shadowquic bean should preserve sunnyquic fields through kryo serialization`() {
        val source = ShadowQUICBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            username = "user"
            password = "pass"
            sni = "tls.example.com"
            subProtocol = ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC
            extraPaths = "1.1.1.1:443"
            maxPaths = 1
            certificates = "cert-1\ncert-2"
            keepAliveInterval = 15
            mtuDiscovery = true
            blackholeDetection = true
            gso = true
        }

        val restored = assertNotNull(
            KryoConverters.shadowQUICDeserialize(KryoConverters.serialize(source)),
        )

        assertEquals(source.subProtocol, restored.subProtocol)
        assertEquals(source.extraPaths, restored.extraPaths)
        assertEquals(source.maxPaths, restored.maxPaths)
        assertEquals(source.certificates, restored.certificates)
        assertEquals(source.keepAliveInterval, restored.keepAliveInterval)
        assertEquals(source.mtuDiscovery, restored.mtuDiscovery)
        assertEquals(source.blackholeDetection, restored.blackholeDetection)
        assertEquals(source.gso, restored.gso)
    }

    @Test
    fun `applyFeatureSettings should copy sunnyquic certificates`() {
        val current = ShadowQUICBean().apply {
            subProtocol = ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC
            certificates = "old-cert"
            extraPaths = "old-path"
            maxPaths = 1
        }
        val incoming = ShadowQUICBean().apply {
            subProtocol = ShadowQUICBean.SUB_PROTOCOL_SUNNY_QUIC
            certificates = "new-cert-1\nnew-cert-2"
            extraPaths = "1.1.1.1:443\n[2606:4700:4700::1111]:443"
            maxPaths = 2
        }

        current.applyFeatureSettings(incoming)

        assertEquals(current.subProtocol, incoming.subProtocol)
        assertEquals(current.certificates, incoming.certificates)
        assertEquals(current.extraPaths, incoming.extraPaths)
        assertEquals(current.maxPaths, incoming.maxPaths)
    }

}
