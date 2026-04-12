package fr.husi.fmt.shadowquic

import fr.husi.database.DataStore
import fr.husi.ktx.getObject
import fr.husi.ktx.toJsonMapKxs
import fr.husi.test.HusiKoinTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

        assertTrue(config.contains("2080"))
        assertTrue(config.contains("\"user\""))
        assertTrue(config.contains("\"pass\""))
        assertTrue(config.contains("\"sni.example.com\""))
        assertTrue(config.contains("\"shadowquic\""))
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

        assertTrue(config.contains("\"sunnyquic\""))
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

}
