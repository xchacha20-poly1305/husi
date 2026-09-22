package fr.husi.fmt.http

import fr.husi.fmt.BeanConverters
import fr.husi.fmt.FmtTestConstant
import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.v2ray.StandardV2RayBean
import fr.husi.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import fr.husi.io.BinaryOutput
import fr.husi.ktx.JSONMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

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
                "certificate_sha256" to listOf("pin-1", "pin-2"),
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
        assertEquals("pin-1\npin-2", bean.certificateSha256)
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

    @Test
    fun `parseHttpOutbound should map http version and fallback`() {
        val json: JSONMap = mutableMapOf(
            "server" to "example.com",
            "server_port" to 443L,
            "version" to 3L,
            "disable_version_fallback" to true,
        )

        val bean = parseHttpOutbound(json)

        assertEquals(HttpBean.HTTP_VERSION_3, bean.httpVersion)
        assertTrue(bean.disableVersionFallback)
    }

    @Test
    fun `parseHttpOutbound should keep default version for unsupported value`() {
        val json: JSONMap = mutableMapOf(
            "server" to "example.com",
            "server_port" to 443L,
            "version" to 0L,
        )

        val bean = parseHttpOutbound(json)

        assertEquals(HttpBean.HTTP_VERSION_1, bean.httpVersion)
    }

    @Test
    fun `parseHttpOutbound should move Host header into host field`() {
        val json: JSONMap = mutableMapOf(
            "server" to "example.com",
            "server_port" to 80L,
            "headers" to mutableMapOf<String, Any?>(
                "Host" to "cdn.example.com",
                "X-Token" to "abc",
            ),
        )

        val bean = parseHttpOutbound(json)

        assertEquals("cdn.example.com", bean.host)
        assertEquals("x-token:abc", bean.headers)
    }

    @Test
    fun `buildRequestTarget should send Host header from headers for HTTP 1`() {
        val bean = HttpBean().apply {
            httpVersion = HttpBean.HTTP_VERSION_1
            headers = "host: cdn.example.com\nX-Token: abc"
        }

        val target = bean.buildRequestTarget()

        assertNull(target.path)
        assertEquals<Map<String, List<String>>?>(
            mapOf(
                "X-Token" to listOf("abc"),
                "Host" to listOf("cdn.example.com"),
            ),
            target.headers,
        )
    }

    @Test
    fun `buildRequestTarget should prefer host field over Host header`() {
        val bean = HttpBean().apply {
            httpVersion = HttpBean.HTTP_VERSION_1
            host = "field.example.com"
            headers = "HOST: header.example.com"
        }

        val target = bean.buildRequestTarget()

        assertEquals<Map<String, List<String>>?>(mapOf("Host" to listOf("field.example.com")), target.headers)
    }

    @Test
    fun `buildRequestTarget should drop Host when path is set`() {
        val bean = HttpBean().apply {
            httpVersion = HttpBean.HTTP_VERSION_1
            host = "cdn.example.com"
            path = "/proxy"
            headers = "Host: header.example.com"
        }

        val target = bean.buildRequestTarget()

        assertEquals("/proxy", target.path)
        assertNull(target.headers)
    }

    @Test
    fun `buildRequestTarget should drop Host and path for HTTP 2 and 3`() {
        for (version in listOf(HttpBean.HTTP_VERSION_2, HttpBean.HTTP_VERSION_3)) {
            val bean = HttpBean().apply {
                httpVersion = version
                host = "cdn.example.com"
                path = "/proxy"
                headers = "Host: header.example.com\nX-Token: abc"
            }

            val target = bean.buildRequestTarget()

            assertNull(target.path)
            assertEquals<Map<String, List<String>>?>(mapOf("X-Token" to listOf("abc")), target.headers)
        }
    }

    @Test
    fun `buildSingBoxOutboundStandardV2RayBean should map http version fields`() = runTest {
        val bean = HttpBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            username = "user"
            password = "pass"
            security = "tls"
            httpVersion = HttpBean.HTTP_VERSION_2
            disableVersionFallback = true
            host = "cdn.example.com"
            path = "/proxy"
        }

        val outbound = assertIs<SingBoxOptions.Outbound_HTTPOptions>(
            buildSingBoxOutboundStandardV2RayBean(bean),
        )

        assertEquals(SingBoxOptions.TYPE_HTTP, outbound.type)
        assertEquals("user", outbound.username)
        assertEquals("pass", outbound.password)
        assertEquals(HttpBean.HTTP_VERSION_2, outbound.version)
        assertEquals(true, outbound.disable_version_fallback)
        assertNull(outbound.path)
        assertNull(outbound.headers)
    }

    @Test
    fun `HttpBean serialize round-trip should preserve http version fields`() {
        val source = HttpBean().apply {
            httpVersion = HttpBean.HTTP_VERSION_3
            disableVersionFallback = true
            host = "cdn.example.com"
        }

        val restored = source.clone()

        assertEquals(HttpBean.HTTP_VERSION_3, restored.httpVersion)
        assertTrue(restored.disableVersionFallback)
        assertEquals("cdn.example.com", restored.host)
    }

    @Test
    fun `deserialize should skip removed udpOverTcp of version 2`() {
        val legacy = LegacyHttpBeanV2().apply {
            serverAddress = "example.com"
            serverPort = 8080
            name = "legacy"
            username = "user"
            path = "/proxy"
        }

        val bean = BeanConverters.httpDeserialize(BeanConverters.serialize(legacy))!!

        assertEquals("example.com", bean.serverAddress)
        assertEquals(8080, bean.serverPort)
        assertEquals("user", bean.username)
        assertEquals("/proxy", bean.path)
        assertEquals("legacy", bean.name)
        assertEquals(HttpBean.HTTP_VERSION_1, bean.httpVersion)
        assertFalse(bean.disableVersionFallback)
    }

    /** Writes what [HttpBean.serialize] emitted before the HTTP version fields replaced udpOverTcp. */
    private class LegacyHttpBeanV2 : StandardV2RayBean() {
        var username = ""
        var password = ""

        override fun serialize(output: BinaryOutput) {
            output.writeInt(2)
            super.serialize(output)
            output.writeString(username)
            output.writeString(password)
            output.writeString(host)
            output.writeString(path)
            output.writeString(headers)
            output.writeBoolean(true)
        }

        override fun clone() = error("unused")
    }
}
