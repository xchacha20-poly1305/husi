package fr.husi.ktx

import fr.husi.fmt.SingBoxOptions
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetsKtTest {

    private val ipv4Address = InetAddress.getByName("192.0.2.1")
    private val ipv6Address = InetAddress.getByName("2001:db8::1")

    @Test
    fun `selectByNetworkStrategy keeps original order by default`() {
        val addresses = listOf(ipv6Address, ipv4Address)

        assertEquals(ipv6Address, addresses.selectByNetworkStrategy(""))
    }

    @Test
    fun `selectByNetworkStrategy prefers IPv6`() {
        val addresses = listOf(ipv4Address, ipv6Address)

        assertEquals(ipv6Address, addresses.selectByNetworkStrategy(SingBoxOptions.STRATEGY_PREFER_IPV6))
    }

    @Test
    fun `selectByNetworkStrategy prefers IPv4`() {
        val addresses = listOf(ipv6Address, ipv4Address)

        assertEquals(ipv4Address, addresses.selectByNetworkStrategy(SingBoxOptions.STRATEGY_PREFER_IPV4))
    }

    @Test
    fun `selectByNetworkStrategy filters IPv6 only`() {
        val addresses = listOf(ipv4Address, ipv6Address)

        assertEquals(ipv6Address, addresses.selectByNetworkStrategy(SingBoxOptions.STRATEGY_IPV6_ONLY))
        assertNull(listOf(ipv4Address).selectByNetworkStrategy(SingBoxOptions.STRATEGY_IPV6_ONLY))
    }

    @Test
    fun `selectByNetworkStrategy filters IPv4 only`() {
        val addresses = listOf(ipv6Address, ipv4Address)

        assertEquals(ipv4Address, addresses.selectByNetworkStrategy(SingBoxOptions.STRATEGY_IPV4_ONLY))
        assertNull(listOf(ipv6Address).selectByNetworkStrategy(SingBoxOptions.STRATEGY_IPV4_ONLY))
    }
}
