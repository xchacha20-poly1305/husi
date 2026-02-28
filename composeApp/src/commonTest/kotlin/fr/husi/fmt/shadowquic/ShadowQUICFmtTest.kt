package fr.husi.fmt.shadowquic

import kotlin.test.Test
import kotlin.test.assertTrue

class ShadowQUICFmtTest {

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
}
