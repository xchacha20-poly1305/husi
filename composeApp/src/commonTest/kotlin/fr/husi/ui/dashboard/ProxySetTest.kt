package fr.husi.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxySetTest {

    @Test
    fun `all proxy set has a stable id and is not selectable`() {
        val proxySet = allProxySet(emptyList())

        assertEquals(ALL_PROXY_SET_ID, proxySet.id)
        assertEquals("All", proxySet.type)
        assertEquals("All proxies", proxySet.tag)
        assertTrue(proxySet.isAll)
        assertFalse(proxySet.selectable)
        assertEquals("", proxySet.selected)
    }

    @Test
    fun `group url test skips direct and block items only`() {
        val items = listOf(
            ProxyItem(tag = "direct", type = "Direct"),
            ProxyItem(tag = "block", type = "Block"),
            ProxyItem(tag = "proxy", type = "Shadowsocks"),
            ProxyItem(tag = "endpoint", type = "WireGuard"),
        )

        assertEquals(
            listOf("proxy", "endpoint"),
            items.filterNot(::skipGroupUrlTest).map(ProxyItem::tag),
        )
    }
}
