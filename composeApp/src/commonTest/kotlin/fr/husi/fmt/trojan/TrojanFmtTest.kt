package fr.husi.fmt.trojan

import fr.husi.fmt.FmtTestConstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrojanFmtTest {

    @Test
    fun `parseTrojan should parse url and set password from username`() {
        val bean = parseTrojan(FmtTestConstant.TROJAN_URL)

        assertEquals("example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("password", bean.password)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("ws", bean.v2rayTransport)
        assertEquals("/ws", bean.path)
        assertEquals("test-node", bean.name)
    }

    @Test
    fun `parseTrojan should force tls security even without explicit security param`() {
        val bean = parseTrojan(FmtTestConstant.TROJAN_DEFAULT_TLS_URL)

        assertTrue(bean.isTLS)
    }

    @Test
    fun `parseTrojan should read peer param as sni`() {
        val bean = parseTrojan(FmtTestConstant.TROJAN_PEER_URL)

        assertEquals("peer.example.com", bean.sni)
    }
}
