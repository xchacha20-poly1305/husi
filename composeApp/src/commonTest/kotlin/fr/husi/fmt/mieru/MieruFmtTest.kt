package fr.husi.fmt.mieru

import fr.husi.fmt.FmtTestConstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MieruFmtTest {

    @Test
    fun `parseMieru should parse url with all fields`() {
        val bean = parseMieru(FmtTestConstant.MIERU_URL)

        assertEquals("example.com", bean.serverAddress)
        assertEquals(8080, bean.serverPort)
        assertEquals("user", bean.username)
        assertEquals("pass", bean.password)
        assertEquals("myprofile", bean.name)
        assertEquals(1400, bean.mtu)
        assertEquals(3, bean.serverMuxNumber)
    }

    @Test
    fun `toUri should preserve serializable fields through parseMieru`() {
        val source = MieruBean().apply {
            serverAddress = "example.com"
            serverPort = 8080
            username = "user"
            password = "pass"
            name = "myprofile"
            mtu = 1200
            serverMuxNumber = 1
        }

        val parsed = parseMieru(source.toUri())

        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.username, parsed.username)
        assertEquals(source.password, parsed.password)
        assertEquals(source.name, parsed.name)
        assertEquals(source.mtu, parsed.mtu)
    }

    @Test
    fun `buildMieruConfig should produce json containing server and user fields`() {
        val bean = MieruBean().apply {
            serverAddress = "example.com"
            serverPort = 8080
            username = "user"
            password = "secret"
            protocol = MieruBean.PROTOCOL_TCP
        }
        bean.initializeDefaultValues()

        val config = bean.buildMieruConfig(port = 2080, logLevel = 0)

        assertTrue(config.contains("\"user\""))
        assertTrue(config.contains("\"secret\""))
        assertTrue(config.contains("example.com"))
        assertTrue(config.contains("2080"))
    }
}
