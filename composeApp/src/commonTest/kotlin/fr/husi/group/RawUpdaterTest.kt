package fr.husi.group

import fr.husi.fmt.FmtTestConstant
import fr.husi.fmt.openconnect.OpenConnectBean
import fr.husi.fmt.openvpn.OpenVPNBean
import fr.husi.fmt.v2ray.VMessBean
import fr.husi.fmt.v2ray.VLESSBean
import fr.husi.ktx.b64EncodeOneLine
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RawUpdaterTest {

    @Test
    fun `parseRaw should parse base64 encoded vless links`() = runBlocking {
        val proxies = assertNotNull(RawUpdater.parseRaw(FmtTestConstant.VLESS_GRPC_URL.b64EncodeOneLine()))
        val bean = assertIs<VLESSBean>(proxies.single())

        assertEquals("uuid", bean.uuid)
        assertEquals("test-vless", bean.name)
    }

    @Test
    fun `parseRaw should parse base64 encoded multi line links`() = runBlocking {
        val rawText = listOf(
            FmtTestConstant.VLESS_GRPC_URL,
            FmtTestConstant.VMESS_DUCKSOFT_URL,
        ).joinToString("\n")

        val proxies = assertNotNull(RawUpdater.parseRaw(rawText.b64EncodeOneLine()))

        assertEquals(2, proxies.size)
        assertTrue(proxies.any { it is VLESSBean })
        assertTrue(proxies.any { it is VMessBean })
    }

    @Test
    fun `parseRaw should parse plain multi line links`() = runBlocking {
        val rawText = listOf(
            FmtTestConstant.VLESS_GRPC_URL,
            FmtTestConstant.VMESS_DUCKSOFT_URL,
        ).joinToString("\n")

        val proxies = assertNotNull(RawUpdater.parseRaw(rawText))

        assertEquals(2, proxies.size)
        assertTrue(proxies.any { it is VLESSBean })
        assertTrue(proxies.any { it is VMessBean })
    }

    @Test
    fun `parseRaw should recognize OpenConnect and OpenVPN sing-box endpoints`() = runBlocking {
        val rawConfig = """
            {
              "endpoints": [
                {
                  "type": "openconnect",
                  "tag": "openconnect",
                  "server": "https://vpn.example.com"
                },
                {
                  "type": "openvpn-client",
                  "tag": "openvpn",
                  "server": "vpn.example.com",
                  "server_port": 443
                }
              ]
            }
        """.trimIndent()

        val proxies = assertNotNull(RawUpdater.parseRaw(rawConfig))

        assertEquals(2, proxies.size)
        assertIs<OpenConnectBean>(proxies[0])
        assertIs<OpenVPNBean>(proxies[1])
    }

    @Test
    fun `parseRaw should recognize OpenConnect configuration`() = runBlocking {
        val config = """
            server=https://vpn.example.com
            protocol=anyconnect
            user=alice
        """.trimIndent()

        val bean = assertIs<OpenConnectBean>(assertNotNull(RawUpdater.parseRaw(config, "work.conf")).single())

        assertEquals("work", bean.name)
        assertEquals("https://vpn.example.com", bean.server)
        assertEquals("anyconnect", bean.flavor)
        assertEquals("alice", bean.username)
    }

}
