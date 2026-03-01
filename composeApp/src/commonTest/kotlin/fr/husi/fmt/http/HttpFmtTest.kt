package fr.husi.fmt.http

import fr.husi.fmt.FmtTestConstant
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpFmtTest {

    @Test
    fun `parseHttp should parse https url and detect tls`() {
        val bean = parseHttp(FmtTestConstant.HTTP_AUTH_TLS_URL)

        assertEquals("example.com", bean.serverAddress)
        assertEquals(443, bean.serverPort)
        assertEquals("user", bean.username)
        assertEquals("pass", bean.password)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("test-node", bean.name)
        assertTrue(bean.isTLS)
    }

    @Test
    fun `parseHttp should use default port 443 for https and 80 for http`() {
        val beanHttps = parseHttp(FmtTestConstant.HTTPS_DEFAULT_PORT_URL)
        val beanHttp = parseHttp(FmtTestConstant.HTTP_DEFAULT_PORT_URL)

        assertEquals(443, beanHttps.serverPort)
        assertEquals(80, beanHttp.serverPort)
    }

    @Test
    fun `parseHttp should not set isTLS for http scheme`() {
        val bean = parseHttp(FmtTestConstant.HTTP_CUSTOM_PORT_URL)

        assertEquals(8080, bean.serverPort)
        assertTrue(!bean.isTLS)
    }

    @Test
    fun `toUri should preserve fields through parseHttp`() {
        val source = HttpBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            username = "user"
            password = "pass"
            sni = "sni.example.com"
            name = "test-node"
            security = "tls"
        }

        val parsed = parseHttp(source.toUri())

        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.username, parsed.username)
        assertEquals(source.password, parsed.password)
        assertEquals(source.sni, parsed.sni)
        assertEquals(source.name, parsed.name)
        assertEquals(source.isTLS, parsed.isTLS)
    }

    @Test
    fun `parseHttpOutbound should map tag server credentials and path`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "http-node",
            "server" to "example.com",
            "server_port" to 8080L,
            "username" to "user",
            "password" to "pass",
            "path" to "/proxy",
        )

        val bean = parseHttpOutbound(json)

        assertEquals("http-node", bean.name)
        assertEquals("example.com", bean.serverAddress)
        assertEquals(8080, bean.serverPort)
        assertEquals("user", bean.username)
        assertEquals("pass", bean.password)
        assertEquals("/proxy", bean.path)
    }

    @Test
    fun `parseHttpOutbound should map tls fields when tls is enabled`() {
        val json: JSONMap = mutableMapOf(
            "tag" to "https-node",
            "server" to "example.com",
            "server_port" to 443L,
            "tls" to mutableMapOf<String, Any?>(
                "enabled" to true,
                "server_name" to "sni.example.com",
                "insecure" to true,
                "alpn" to listOf("h2", "http/1.1"),
                "certificate" to listOf("cert-1"),
                "certificate_public_key_sha256" to "sha-1",
                "client_certificate" to listOf("client-cert"),
                "client_key" to listOf("client-key"),
                "utls" to mutableMapOf<String, Any?>(
                    "enabled" to true,
                    "fingerprint" to "chrome",
                ),
                "ech" to mutableMapOf<String, Any?>(
                    "enabled" to true,
                    "config" to listOf("ech-cfg"),
                ),
            ),
        )

        val bean = parseHttpOutbound(json)

        assertTrue(bean.isTLS)
        assertEquals("sni.example.com", bean.sni)
        assertTrue(bean.allowInsecure)
        assertEquals("h2,http/1.1", bean.alpn)
        assertEquals("cert-1", bean.certificates)
        assertEquals("sha-1", bean.certPublicKeySha256)
        assertEquals("client-cert", bean.clientCert)
        assertEquals("client-key", bean.clientKey)
        assertEquals("chrome", bean.utlsFingerprint)
        assertTrue(bean.ech)
        assertEquals("ech-cfg", bean.echConfig)
    }

    @Test
    fun `parseHttpOutbound should not set tls when tls enabled is false`() {
        val json: JSONMap = mutableMapOf(
            "server" to "example.com",
            "server_port" to 8080L,
            "tls" to mutableMapOf<String, Any?>(
                "enabled" to false,
                "server_name" to "should-not-be-set",
            ),
        )

        val bean = parseHttpOutbound(json)

        assertTrue(!bean.isTLS)
    }
}
