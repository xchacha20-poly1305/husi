package fr.husi.ui.dashboard

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProxySetFilterTest {

    private val auto = ProxySet(
        tag = "Auto",
        displayType = "URLTest",
        items = listOf(
            ProxyItem(tag = "Hong Kong", type = "ss"),
            ProxyItem(tag = "US LAX", type = "vless"),
            ProxyItem(tag = "Germany", type = "vmess"),
        ),
    )
    private val fallback = ProxySet(
        tag = "Fallback",
        displayType = "Selector",
        items = listOf(
            ProxyItem(tag = "Hong Kong 2", type = "ss"),
            ProxyItem(tag = "Japan", type = "hysteria2"),
        ),
    )
    private val sets = listOf(auto, fallback)

    private fun List<ProxySet>.outboundTags(search: String): List<String> {
        return filterBy(ProxySetQuery(globalSearch = search, mode = ProxySetSearchMode.Outbound))
            .flatMap { set -> set.items.map(ProxyItem::tag) }
    }

    private fun groupOf(vararg items: ProxyItem) = ProxySet(
        tag = "Group",
        displayType = "Selector",
        items = items.toList(),
    )

    @Test
    fun `keyword matching is case-insensitive and requires every word`() {
        val group = listOf(groupOf(ProxyItem(tag = "Hong Kong Node")))

        assertEquals(listOf("Hong Kong Node"), group.outboundTags("hong"))
        assertEquals(listOf("Hong Kong Node"), group.outboundTags("HONG kong"))
        assertEquals(emptyList(), group.outboundTags("hong japan"))
    }

    @Test
    fun `keyword matching handles emoji`() {
        val group = listOf(
            groupOf(
                ProxyItem(tag = "🇭🇰 Hong Kong 01"),
                ProxyItem(tag = "🇭🇰Hong Kong 02"),
                ProxyItem(tag = "🚀 Auto"),
            ),
        )

        assertEquals(listOf("🇭🇰 Hong Kong 01", "🇭🇰Hong Kong 02"), group.outboundTags("🇭🇰"))
        assertEquals(listOf("🇭🇰Hong Kong 02"), group.outboundTags("🇭🇰 hong 02"))
        assertEquals(listOf("🚀 Auto"), group.outboundTags("🚀"))
        assertEquals(emptyList(), group.outboundTags("🇯🇵"))
        assertEquals(emptyList(), group.outboundTags("🇭🇰 japan"))
    }

    @Test
    fun `emoji query filters outbounds and proxy sets`() {
        val emojiSets = listOf(
            ProxySet(
                tag = "🚀 Auto",
                displayType = "URLTest",
                items = listOf(
                    ProxyItem(tag = "🇭🇰 Hong Kong", type = "shadowsocks"),
                    ProxyItem(tag = "🇺🇸 US LAX", type = "vless"),
                ),
            ),
            ProxySet(
                tag = "🐟 Fallback",
                displayType = "Selector",
                items = listOf(
                    ProxyItem(tag = "🇯🇵 Japan", type = "hysteria2"),
                    ProxyItem(tag = "🇭🇰 Hong Kong 2", type = "shadowsocks"),
                ),
            ),
        )

        val outbounds = emojiSets.filterBy(
            ProxySetQuery(globalSearch = "🇭🇰", mode = ProxySetSearchMode.Outbound),
        )
        assertEquals(listOf("🚀 Auto", "🐟 Fallback"), outbounds.map(ProxySet::tag))
        assertEquals(listOf("🇭🇰 Hong Kong"), outbounds[0].items.map(ProxyItem::tag))
        assertEquals(listOf("🇭🇰 Hong Kong 2"), outbounds[1].items.map(ProxyItem::tag))

        val proxySets = emojiSets.filterBy(
            ProxySetQuery(globalSearch = "🐟", mode = ProxySetSearchMode.ProxySet),
        )
        assertEquals(listOf("🐟 Fallback"), proxySets.map(ProxySet::tag))
    }

    @Test
    fun `keyword matching handles chinese`() {
        val group = listOf(
            groupOf(
                ProxyItem(tag = "香港 01 IPLC"),
                ProxyItem(tag = "香港02"),
                ProxyItem(tag = "🇭🇰 香港 IPLC"),
            ),
        )

        assertEquals(listOf("香港 01 IPLC", "香港02", "🇭🇰 香港 IPLC"), group.outboundTags("香港"))
        assertEquals(listOf("香港02"), group.outboundTags("香港 02"))
        assertEquals(listOf("香港 01 IPLC", "🇭🇰 香港 IPLC"), group.outboundTags("香港 iplc"))
        assertEquals(emptyList(), group.outboundTags("日本"))
        assertEquals(emptyList(), group.outboundTags("香港 日本"))
    }

    @Test
    fun `chinese query filters outbounds and proxy sets`() {
        val chineseSets = listOf(
            ProxySet(
                tag = "自动选择",
                displayType = "URLTest",
                items = listOf(
                    ProxyItem(tag = "香港 01", type = "shadowsocks"),
                    ProxyItem(tag = "美国 洛杉矶", type = "vless"),
                ),
            ),
            ProxySet(
                tag = "故障转移",
                displayType = "Selector",
                items = listOf(
                    ProxyItem(tag = "日本 东京", type = "hysteria2"),
                    ProxyItem(tag = "香港 02", type = "shadowsocks"),
                ),
            ),
        )

        val outbounds = chineseSets.filterBy(
            ProxySetQuery(globalSearch = "香港", mode = ProxySetSearchMode.Outbound),
        )
        assertEquals(listOf("自动选择", "故障转移"), outbounds.map(ProxySet::tag))
        assertEquals(listOf("香港 01"), outbounds[0].items.map(ProxyItem::tag))
        assertEquals(listOf("香港 02"), outbounds[1].items.map(ProxyItem::tag))

        val proxySets = chineseSets.filterBy(
            ProxySetQuery(globalSearch = "故障", mode = ProxySetSearchMode.ProxySet),
        )
        assertEquals(listOf("故障转移"), proxySets.map(ProxySet::tag))

        val inGroup = chineseSets.filterBy(
            ProxySetQuery(groupSearch = "东京", searchingProxyGroup = chineseSets[1].id),
        )
        assertEquals(chineseSets[0].items, inGroup[0].items)
        assertEquals(listOf("日本 东京"), inGroup[1].items.map(ProxyItem::tag))
    }

    @Test
    fun `outbound mode drops empty groups and filters items`() {
        val result = sets.filterBy(
            ProxySetQuery(globalSearch = "hong", mode = ProxySetSearchMode.Outbound),
        )

        assertEquals(listOf("Auto", "Fallback"), result.map(ProxySet::tag))
        assertEquals(listOf("Hong Kong"), result[0].items.map(ProxyItem::tag))
        assertEquals(listOf("Hong Kong 2"), result[1].items.map(ProxyItem::tag))

        val germanyOnly = sets.filterBy(
            ProxySetQuery(globalSearch = "germany", mode = ProxySetSearchMode.Outbound),
        )
        assertEquals(listOf("Auto"), germanyOnly.map(ProxySet::tag))
        assertEquals(listOf("Germany"), germanyOnly.single().items.map(ProxyItem::tag))
    }

    private val protocols = listOf(
        groupOf(
            ProxyItem(tag = "HK", type = "shadowsocks", displayType = "Shadowsocks"),
            ProxyItem(tag = "US", type = "vmess", displayType = "VMess"),
            ProxyItem(tag = "TW", type = "vless", displayType = "VLESS"),
            ProxyItem(tag = "JP", type = "hysteria2", displayType = "Hysteria2"),
            ProxyItem(tag = "SG", type = "anytls", displayType = "AnyTLS"),
            ProxyItem(tag = "KR", type = "shadowtls", displayType = "ShadowTLS"),
            ProxyItem(tag = "Relay", type = "openvpn-client", displayType = "OpenVPN Client"),
        ),
    )

    @Test
    fun `outbound search matches item type`() {
        assertEquals(listOf("US LAX"), sets.outboundTags("vless"))
        assertEquals(listOf("Relay"), protocols.outboundTags("openvpn-client"))
    }

    @Test
    fun `outbound search matches item display type`() {
        assertEquals(listOf("TW"), protocols.outboundTags("VLESS"))
        assertEquals(listOf("Relay"), protocols.outboundTags("openvpn client"))
    }

    @Test
    fun `outbound search combines tag and type keywords`() {
        assertEquals(listOf("Hong Kong", "Hong Kong 2"), sets.outboundTags("hong ss"))
    }

    @Test
    fun `type keywords match substrings of display types`() {
        assertEquals(listOf("SG", "KR"), protocols.outboundTags("tls"))
        assertEquals(listOf("HK", "KR"), protocols.outboundTags("shadow"))
        assertEquals(listOf("US"), protocols.outboundTags("mess"))
    }

    @Test
    fun `proxy set search matches group display type`() {
        val result = sets.filterBy(
            ProxySetQuery(globalSearch = "urltest", mode = ProxySetSearchMode.ProxySet),
        )

        assertEquals(listOf("Auto"), result.map(ProxySet::tag))
        assertEquals(auto.items.map(ProxyItem::tag), result.single().items.map(ProxyItem::tag))

        val selector = sets.filterBy(
            ProxySetQuery(globalSearch = "selector", mode = ProxySetSearchMode.ProxySet),
        )
        assertEquals(listOf("Fallback"), selector.map(ProxySet::tag))

        val urlTest = sets.filterBy(
            ProxySetQuery(globalSearch = "url test", mode = ProxySetSearchMode.ProxySet),
        )
        assertEquals(listOf("Auto"), urlTest.map(ProxySet::tag))
    }

    @Test
    fun `in-group search matches item type`() {
        val result = sets.filterBy(
            ProxySetQuery(groupSearch = "hysteria", searchingProxyGroup = fallback.id),
        )

        assertEquals(auto.items.map(ProxyItem::tag), result[0].items.map(ProxyItem::tag))
        assertEquals(listOf("Japan"), result[1].items.map(ProxyItem::tag))
    }

    @Test
    fun `proxy set mode keeps all items of a matched group`() {
        val result = sets.filterBy(
            ProxySetQuery(globalSearch = "auto", mode = ProxySetSearchMode.ProxySet),
        )

        assertEquals(listOf("Auto"), result.map(ProxySet::tag))
        assertEquals(auto.items.map(ProxyItem::tag), result.single().items.map(ProxyItem::tag))
    }

    @Test
    fun `in-group filtering touches only the target group`() {
        val result = sets.filterBy(
            ProxySetQuery(
                groupSearch = "hong",
                searchingProxyGroup = auto.id,
            ),
        )

        assertEquals(listOf("Auto", "Fallback"), result.map(ProxySet::tag))
        assertEquals(listOf("Hong Kong"), result[0].items.map(ProxyItem::tag))
        assertEquals(fallback.items.map(ProxyItem::tag), result[1].items.map(ProxyItem::tag))
    }

    @Test
    fun `blank query is a no-op`() {
        assertEquals(sets, sets.filterBy(ProxySetQuery()))
        assertEquals(
            sets,
            sets.filterBy(ProxySetQuery(globalSearch = "   ", mode = ProxySetSearchMode.Outbound)),
        )
        assertEquals(
            sets,
            sets.filterBy(ProxySetQuery(groupSearch = "  ", searchingProxyGroup = auto.id)),
        )
    }

    @Test
    fun `force expansion follows the active search`() {
        val outboundSearch = ProxySetQuery(globalSearch = "hong", mode = ProxySetSearchMode.Outbound)
        assertTrue(outboundSearch.forcesExpanded(auto))

        val proxySetSearch = ProxySetQuery(globalSearch = "auto", mode = ProxySetSearchMode.ProxySet)
        assertFalse(proxySetSearch.forcesExpanded(auto))

        val groupSearch = ProxySetQuery(searchingProxyGroup = auto.id)
        assertTrue(groupSearch.forcesExpanded(auto))
        assertFalse(groupSearch.forcesExpanded(fallback))

        assertFalse(ProxySetQuery().forcesExpanded(auto))
    }
}
