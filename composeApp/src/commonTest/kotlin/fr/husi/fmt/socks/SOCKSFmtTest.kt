package fr.husi.fmt.socks

import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals

class SOCKSFmtTest {

    @Test
    fun `parseSOCKS should parse socks5 url with credentials`() {
        val bean = parseSOCKS("socks5://user:pass@example.com:1080#test-node")

        assertEquals("example.com", bean.serverAddress)
        assertEquals(1080, bean.serverPort)
        assertEquals("user", bean.username)
        assertEquals("pass", bean.password)
        assertEquals(SOCKSBean.PROTOCOL_SOCKS5, bean.protocol)
        assertEquals("test-node", bean.name)
    }

    @Test
    fun `parseSOCKS should detect socks4 protocol and default port`() {
        val bean = parseSOCKS("socks4://example.com")

        assertEquals(SOCKSBean.PROTOCOL_SOCKS4, bean.protocol)
        assertEquals(1080, bean.serverPort)
    }

    @Test
    fun `parseSOCKS should detect socks4a protocol`() {
        val bean = parseSOCKS("socks4a://example.com:1234")

        assertEquals(SOCKSBean.PROTOCOL_SOCKS4A, bean.protocol)
        assertEquals(1234, bean.serverPort)
    }

    @Test
    fun `toUri should preserve fields through parseSOCKS`() {
        val source = SOCKSBean().apply {
            serverAddress = "example.com"
            serverPort = 1080
            username = "user"
            password = "pass"
            protocol = SOCKSBean.PROTOCOL_SOCKS5
            name = "node"
        }

        val parsed = parseSOCKS(source.toUri())

        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.username, parsed.username)
        assertEquals(source.password, parsed.password)
        assertEquals(source.protocol, parsed.protocol)
        assertEquals(source.name, parsed.name)
    }

    @Test
    fun `buildSingBoxOutboundSocksBean should map type server port and credentials`() {
        val bean = SOCKSBean().apply {
            serverAddress = "example.com"
            serverPort = 1080
            username = "user"
            password = "pass"
            protocol = SOCKSBean.PROTOCOL_SOCKS5
        }

        val outbound = buildSingBoxOutboundSocksBean(bean)

        assertEquals("socks", outbound.type)
        assertEquals("example.com", outbound.server)
        assertEquals(1080, outbound.server_port)
        assertEquals("user", outbound.username)
        assertEquals("pass", outbound.password)
        assertEquals("5", outbound.version)
    }

    @Test
    fun `buildSingBoxOutboundSocksBean should set version 4 for socks4`() {
        val bean = SOCKSBean().apply {
            serverAddress = "example.com"
            serverPort = 1080
            protocol = SOCKSBean.PROTOCOL_SOCKS4
        }

        val outbound = buildSingBoxOutboundSocksBean(bean)

        assertEquals("4", outbound.version)
    }

    @Test
    fun `parseSocksOutbound should map tag server credentials and version`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "node-1",
            "server" to "example.com",
            "server_port" to 1080L,
            "username" to "user",
            "password" to "pass",
            "version" to "4a",
        )

        val bean = parseSocksOutbound(json)

        assertEquals("node-1", bean.name)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(1080, bean.serverPort)
        assertEquals("user", bean.username)
        assertEquals("pass", bean.password)
        assertEquals(SOCKSBean.PROTOCOL_SOCKS4A, bean.protocol)
    }
}
