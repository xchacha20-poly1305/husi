package fr.husi.ktx

import fr.husi.fmt.SingBoxOptions
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    @Test
    fun `isLoopbackHost accepts localhost and loopback literals`() {
        assertTrue("localhost".isLoopbackHost())
        assertTrue("LocalHost".isLoopbackHost())
        assertTrue("127.0.0.1".isLoopbackHost())
        assertTrue("127.8.8.8".isLoopbackHost())
        assertTrue("::1".isLoopbackHost())
        assertTrue("[::1]".isLoopbackHost())
    }

    @Test
    fun `isLoopbackHost rejects other hosts without resolving them`() {
        assertFalse("example.com".isLoopbackHost())
        assertFalse("8.8.8.8".isLoopbackHost())
        assertFalse("face.fee".isLoopbackHost())
    }
}
