package fr.husi.fmt.internal

import fr.husi.fmt.SingBoxOptions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProxySetFmtTest {

    @Test
    fun `buildSingBoxOutboundProxySetBean should build selector outbound`() {
        val bean = ProxySetBean().apply {
            management = ProxySetBean.MANAGEMENT_SELECTOR
            interruptExistConnections = true
        }

        val outbounds = listOf("proxy-1", "proxy-2")
        val outbound = buildSingBoxOutboundProxySetBean(bean, outbounds)

        val selector = assertIs<SingBoxOptions.Outbound_SelectorOptions>(outbound)
        assertEquals(SingBoxOptions.TYPE_SELECTOR, selector.type)
        assertEquals(outbounds, selector.outbounds?.toList())
        assertTrue(selector.interrupt_exist_connections == true)
    }

    @Test
    fun `buildSingBoxOutboundProxySetBean should build urltest outbound`() {
        val bean = ProxySetBean().apply {
            management = ProxySetBean.MANAGEMENT_URLTEST
            testURL = "https://www.gstatic.com/generate_204"
            testInterval = "3m"
            testIdleTimeout = "10m"
            testTolerance = 50
            interruptExistConnections = false
        }

        val outbounds = listOf("proxy-1", "proxy-2", "proxy-3")
        val outbound = buildSingBoxOutboundProxySetBean(bean, outbounds)

        val urltest = assertIs<SingBoxOptions.Outbound_URLTestOptions>(outbound)
        assertEquals(SingBoxOptions.TYPE_URLTEST, urltest.type)
        assertEquals(outbounds, urltest.outbounds?.toList())
        assertEquals("https://www.gstatic.com/generate_204", urltest.url)
        assertEquals("3m", urltest.interval)
        assertEquals("10m", urltest.idle_timeout)
        assertEquals(50, urltest.tolerance)
    }
}
