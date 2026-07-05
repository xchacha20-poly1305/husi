package fr.husi.fmt.snell

import fr.husi.fmt.KryoConverters
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SnellFmtTest {

    @Test
    fun `buildSingBoxOutboundSnellBean should map v4 fields`() {
        val bean = SnellBean().apply {
            serverAddress = "snell.example.com"
            serverPort = 8443
            version = SnellBean.VERSION_4
            psk = "shared-secret"
            userKey = "user-secret"
            reuse = true
            obfsMode = "http"
            obfsHost = "front.example.com"
        }

        val outbound = buildSingBoxOutboundSnellBean(bean)

        assertEquals(SingBoxOptions.TYPE_SNELL, outbound.type)
        assertEquals("snell.example.com", outbound.server)
        assertEquals(8443, outbound.server_port)
        assertEquals(SnellBean.VERSION_4, outbound.version)
        assertEquals("shared-secret", outbound.psk)
        assertEquals("user-secret", outbound.userkey)
        assertEquals(true, outbound.reuse)
        assertEquals("http", outbound.obfs_mode)
        assertEquals("front.example.com", outbound.obfs_host)
        assertNull(outbound.mode)
    }

    @Test
    fun `buildSingBoxOutboundSnellBean should map v6 mode and omit false reuse`() {
        val bean = SnellBean().apply {
            serverAddress = "snell.example.com"
            serverPort = 443
            version = SnellBean.VERSION_6
            psk = "shared-secret"
            mode = "tls"
        }

        val outbound = buildSingBoxOutboundSnellBean(bean)

        assertEquals(SnellBean.VERSION_6, outbound.version)
        assertEquals("tls", outbound.mode)
        assertNull(outbound.reuse)
        assertNull(outbound.userkey)
        assertNull(outbound.obfs_mode)
        assertNull(outbound.obfs_host)
    }

    @Test
    fun `parseSnellOutbound should map all supported fields`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "snell-node",
            "server" to "server.example.com",
            "server_port" to 1443L,
            "version" to 4L,
            "psk" to "psk-value",
            "userkey" to "user-key-value",
            "reuse" to true,
            "obfs_mode" to "tls",
            "obfs_host" to "cdn.example.com",
        )

        val bean = parseSnellOutbound(json)

        assertEquals("snell-node", bean.name)
        assertEquals("server.example.com", bean.serverAddress)
        assertEquals(1443, bean.serverPort)
        assertEquals(SnellBean.VERSION_4, bean.version)
        assertEquals("psk-value", bean.psk)
        assertEquals("user-key-value", bean.userKey)
        assertEquals(true, bean.reuse)
        assertEquals("tls", bean.obfsMode)
        assertEquals("cdn.example.com", bean.obfsHost)
    }

    @Test
    fun `SnellBean should survive Kryo serialization`() {
        val source = SnellBean().apply {
            serverAddress = "server.example.com"
            serverPort = 9443
            name = "snell-node"
            customOutboundJson = """{"type":"snell"}"""
            version = SnellBean.VERSION_6
            psk = "psk"
            userKey = "user-key"
            reuse = false
            mode = "tls"
        }

        val parsed = KryoConverters.deserialize(SnellBean(), KryoConverters.serialize(source))

        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.name, parsed.name)
        assertEquals(source.customOutboundJson, parsed.customOutboundJson)
        assertEquals(source.version, parsed.version)
        assertEquals(source.psk, parsed.psk)
        assertEquals(source.userKey, parsed.userKey)
        assertFalse(parsed.reuse)
        assertEquals(source.mode, parsed.mode)
    }

    @Test
    fun `SnellBean serialization should only preserve v4 obfs fields`() {
        val source = SnellBean().apply {
            version = SnellBean.VERSION_4
            psk = "psk"
            obfsMode = "http"
            obfsHost = "front.example.com"
            mode = "tls"
        }

        val parsed = KryoConverters.deserialize(SnellBean(), KryoConverters.serialize(source))

        assertEquals(SnellBean.VERSION_4, parsed.version)
        assertEquals("http", parsed.obfsMode)
        assertEquals("front.example.com", parsed.obfsHost)
        assertEquals("", parsed.mode)
    }

    @Test
    fun `SnellBean serialization should only preserve v6 mode field`() {
        val source = SnellBean().apply {
            version = SnellBean.VERSION_6
            psk = "psk"
            obfsMode = "http"
            obfsHost = "front.example.com"
            mode = "tls"
        }

        val parsed = KryoConverters.deserialize(SnellBean(), KryoConverters.serialize(source))

        assertEquals(SnellBean.VERSION_6, parsed.version)
        assertEquals("", parsed.obfsMode)
        assertEquals("", parsed.obfsHost)
        assertEquals("tls", parsed.mode)
    }
}
