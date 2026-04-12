package fr.husi.fmt.shadowquic

import fr.husi.database.DataStore
import fr.husi.fmt.KryoConverters
import fr.husi.ktx.getObject
import fr.husi.ktx.toJsonMapKxs
import fr.husi.test.HusiKoinTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ShadowQUICFmtTest : HusiKoinTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
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
