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

    @Test
    fun `expanding url test targets reaches the leaves of nested groups`() {
        val outer = ProxyItem(tag = "outer", type = "Selector")
        val inner = ProxyItem(tag = "inner", type = "URLTest")
        val members = mapOf(
            "outer" to listOf(ProxyItem(tag = "a", type = "Shadowsocks"), inner),
            "inner" to listOf(
                ProxyItem(tag = "b", type = "Trojan"),
                ProxyItem(tag = "direct", type = "Direct"),
            ),
        )

        assertEquals(
            listOf("a", "b"),
            expandUrlTestTargets(listOf(outer), members).map(ProxyItem::tag),
        )
    }

    @Test
    fun `expanding url test targets measures a shared leaf once`() {
        val members = mapOf(
            "left" to listOf(ProxyItem(tag = "shared", type = "Trojan")),
            "right" to listOf(ProxyItem(tag = "shared", type = "Trojan")),
        )
        val items = listOf(
            ProxyItem(tag = "left", type = "Selector"),
            ProxyItem(tag = "right", type = "Selector"),
        )

        assertEquals(listOf("shared"), expandUrlTestTargets(items, members).map(ProxyItem::tag))
    }

    @Test
    fun `expanding url test targets survives a group cycle`() {
        val members = mapOf(
            "left" to listOf(
                ProxyItem(tag = "right", type = "Selector"),
                ProxyItem(tag = "a", type = "Trojan"),
            ),
            "right" to listOf(ProxyItem(tag = "left", type = "Selector")),
        )

        assertEquals(
            listOf("a"),
            expandUrlTestTargets(
                listOf(ProxyItem(tag = "left", type = "Selector")),
                members,
            ).map(ProxyItem::tag),
        )
    }
}
