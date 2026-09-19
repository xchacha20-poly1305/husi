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
        assertEquals("All", proxySet.displayType)
        assertEquals("All proxies", proxySet.tag)
        assertTrue(proxySet.isAll)
        assertFalse(proxySet.selectable)
        assertEquals("", proxySet.selected)
    }

    @Test
    fun `group url test skips direct and block items only`() {
        val items = listOf(
            ProxyItem(tag = "direct", type = "direct"),
            ProxyItem(tag = "block", type = "block"),
            ProxyItem(tag = "proxy", type = "shadowsocks"),
            ProxyItem(tag = "endpoint", type = "wireguard"),
        )

        assertEquals(
            listOf("proxy", "endpoint"),
            items.filterNot(::skipGroupUrlTest).map(ProxyItem::tag),
        )
    }

    @Test
    fun `expanding url test targets reaches the leaves of nested groups`() {
        val outer = ProxyItem(tag = "outer", type = "selector")
        val inner = ProxyItem(tag = "inner", type = "urltest")
        val members = mapOf(
            "outer" to listOf(ProxyItem(tag = "a", type = "shadowsocks"), inner),
            "inner" to listOf(
                ProxyItem(tag = "b", type = "trojan"),
                ProxyItem(tag = "direct", type = "direct"),
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
            "left" to listOf(ProxyItem(tag = "shared", type = "trojan")),
            "right" to listOf(ProxyItem(tag = "shared", type = "trojan")),
        )
        val items = listOf(
            ProxyItem(tag = "left", type = "selector"),
            ProxyItem(tag = "right", type = "selector"),
        )

        assertEquals(listOf("shared"), expandUrlTestTargets(items, members).map(ProxyItem::tag))
    }

    @Test
    fun `expanding url test targets survives a group cycle`() {
        val members = mapOf(
            "left" to listOf(
                ProxyItem(tag = "right", type = "selector"),
                ProxyItem(tag = "a", type = "trojan"),
            ),
            "right" to listOf(ProxyItem(tag = "left", type = "selector")),
        )

        assertEquals(
            listOf("a"),
            expandUrlTestTargets(
                listOf(ProxyItem(tag = "left", type = "selector")),
                members,
            ).map(ProxyItem::tag),
        )
    }

    @Test
    fun `filtering proxy items keeps every item for a blank query`() {
        val items = listOf(
            ProxyItem(tag = "🇭🇰 Hong Kong", type = "shadowsocks"),
            ProxyItem(tag = "🇺🇸 US - LAX", type = "hysteria2"),
        )

        assertEquals(items, filterProxyItems(items, ""))
        assertEquals(items, filterProxyItems(items, "   "))
    }

    @Test
    fun `filtering proxy items matches tag and type ignoring case`() {
        val items = listOf(
            ProxyItem(tag = "🇭🇰 Hong Kong", type = "shadowsocks"),
            ProxyItem(tag = "🇺🇸 US - LAX", type = "hysteria2"),
            ProxyItem(tag = "🇩🇪 Frankfurt", type = "vmess"),
        )

        assertEquals(
            listOf("🇺🇸 US - LAX"),
            filterProxyItems(items, "lax").map(ProxyItem::tag),
        )
        assertEquals(
            listOf("🇭🇰 Hong Kong"),
            filterProxyItems(items, " ShadowSocks ").map(ProxyItem::tag),
        )
        assertTrue(filterProxyItems(items, "tokyo").isEmpty())
    }

    @Test
    fun `outbound search keeps the matched outbounds and drops the sets without one`() {
        val proxySets = listOf(
            ProxySet(
                tag = "Auto",
                displayType = "URLTest",
                items = listOf(
                    ProxyItem(tag = "🇭🇰 Hong Kong", type = "shadowsocks"),
                    ProxyItem(tag = "🇺🇸 US - LAX", type = "hysteria2"),
                ),
            ),
            ProxySet(
                tag = "Manual",
                displayType = "Selector",
                items = listOf(ProxyItem(tag = "🇩🇪 Frankfurt", type = "vmess")),
            ),
        )

        val matched = filterProxySets(proxySets, "us", ProxySearchMode.OUTBOUND)

        assertEquals(listOf("Auto"), matched.map(ProxySet::tag))
        assertEquals(listOf("🇺🇸 US - LAX"), matched.single().items.map(ProxyItem::tag))
    }

    @Test
    fun `proxy set search keeps whole sets whose tag or type matches`() {
        val proxySets = listOf(
            ProxySet(
                tag = "Auto",
                displayType = "URLTest",
                items = listOf(ProxyItem(tag = "🇭🇰 Hong Kong", type = "shadowsocks")),
            ),
            ProxySet(
                tag = "Manual",
                displayType = "Selector",
                items = listOf(ProxyItem(tag = "🇩🇪 Frankfurt", type = "vmess")),
            ),
        )

        val byTag = filterProxySets(proxySets, "manual", ProxySearchMode.PROXY_SET)
        assertEquals(listOf("Manual"), byTag.map(ProxySet::tag))
        assertEquals(proxySets[1].items, byTag.single().items)

        assertEquals(
            listOf("Auto"),
            filterProxySets(proxySets, "urltest", ProxySearchMode.PROXY_SET).map(ProxySet::tag),
        )
    }

    @Test
    fun `a blank query leaves the proxy sets untouched in both modes`() {
        val proxySets = listOf(
            ProxySet(
                tag = "Auto",
                displayType = "URLTest",
                items = listOf(ProxyItem(tag = "🇭🇰 Hong Kong", type = "shadowsocks")),
            ),
        )

        assertEquals(proxySets, filterProxySets(proxySets, "  ", ProxySearchMode.OUTBOUND))
        assertEquals(proxySets, filterProxySets(proxySets, "", ProxySearchMode.PROXY_SET))
    }
}
