package fr.husi.fmt.shadowsocks

import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ShadowsocksFmtTest {

    @Test
    fun `parseShadowsocks should parse 2022-style url`() {
        val bean = parseShadowsocks("ss://2022-blake3-aes-128-gcm:password@example.com:8388#test")

        assertEquals("2022-blake3-aes-128-gcm", bean.method)
        assertEquals("password", bean.password)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(8388, bean.serverPort)
        assertEquals("test", bean.name)
    }

    @Test
    fun `parseShadowsocks should preserve fields in round trip`() {
        val source = ShadowsocksBean().apply {
            serverAddress = "example.com"
            serverPort = 8388
            method = "aes-256-gcm"
            password = "mypassword"
            name = "test-node"
        }

        val parsed = parseShadowsocks(source.toUri())

        assertEquals(source.method, parsed.method)
        assertEquals(source.password, parsed.password)
        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.name, parsed.name)
    }

    @Test
    fun `parseShadowsocks should handle plugin in url`() {
        val source = ShadowsocksBean().apply {
            serverAddress = "example.com"
            serverPort = 8388
            method = "chacha20-ietf-poly1305"
            password = "pass"
            plugin = "obfs-local;obfs=http;obfs-host=example.com"
        }

        val parsed = parseShadowsocks(source.toUri())

        assertEquals("obfs-local;obfs=http;obfs-host=example.com", parsed.plugin)
    }

    @Test
    fun `pluginToStandard should replace obfs-local with simple-obfs`() {
        assertEquals("simple-obfs;obfs=http", pluginToStandard("obfs-local;obfs=http"))
    }

    @Test
    fun `pluginToStandard should not modify non-obfs plugin`() {
        assertEquals("v2ray-plugin;server", pluginToStandard("v2ray-plugin;server"))
    }

    @Test
    fun `buildSingBoxOutboundShadowsocksBean should map method password server and plugin`() {
        val bean = ShadowsocksBean().apply {
            serverAddress = "example.com"
            serverPort = 8388
            method = "aes-256-gcm"
            password = "secret"
            plugin = "obfs-local;obfs=http"
        }

        val outbound = buildSingBoxOutboundShadowsocksBean(bean)

        assertEquals("shadowsocks", outbound.type)
        assertEquals("example.com", outbound.server)
        assertEquals(8388, outbound.server_port)
        assertEquals("aes-256-gcm", outbound.method)
        assertEquals("secret", outbound.password)
        assertEquals("obfs-local", outbound.plugin)
        assertEquals("obfs=http", outbound.plugin_opts)
    }

    @Test
    fun `buildSingBoxOutboundShadowsocksBean should omit plugin fields when plugin is blank`() {
        val bean = ShadowsocksBean().apply {
            serverAddress = "example.com"
            serverPort = 8388
            method = "aes-256-gcm"
            password = "secret"
        }

        val outbound = buildSingBoxOutboundShadowsocksBean(bean)

        assertNull(outbound.plugin)
        assertNull(outbound.plugin_opts)
    }

    @Test
    fun `parseShadowsocksOutbound should map server credentials and method`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "ss-node",
            "server" to "example.com",
            "server_port" to 8388L,
            "method" to "chacha20-ietf-poly1305",
            "password" to "secret",
        )

        val bean = parseShadowsocksOutbound(json)

        assertEquals("ss-node", bean.name)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(8388, bean.serverPort)
        assertEquals("chacha20-ietf-poly1305", bean.method)
        assertEquals("secret", bean.password)
    }

    @Test
    fun `parseShadowsocksOutbound should map plugin and plugin opts`() {
        val json: JSONMap = mutableMapOf(
            "server" to "example.com",
            "server_port" to 8388L,
            "method" to "aes-256-gcm",
            "password" to "pass",
            "plugin" to "simple-obfs",
            "plugin_opts" to "obfs=http",
        )

        val bean = parseShadowsocksOutbound(json)

        // pluginToLocal() converts simple-obfs → obfs-local
        assertEquals("obfs-local;obfs=http", bean.plugin)
    }
}
