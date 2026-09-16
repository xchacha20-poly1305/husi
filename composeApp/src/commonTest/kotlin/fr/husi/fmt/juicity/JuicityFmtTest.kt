package fr.husi.fmt.juicity

import fr.husi.database.DataStore
import fr.husi.fmt.FmtTestConstant
import fr.husi.fmt.LOCALHOST4
import fr.husi.fmt.SingBoxOptions
import fr.husi.ktx.toJsonMapKxs
import fr.husi.test.HusiKoinTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class JuicityFmtTest : HusiKoinTest() {

    override suspend fun postStartKoin() {
        DataStore.configurationStore.reset()
    }

    @Test
    fun `parseJuicity should parse url with all fields`() {
        val bean = parseJuicity(FmtTestConstant.JUICITY_URL)

        assertEquals("example.com", bean.serverAddress)
        assertEquals(8443, bean.serverPort)
        assertEquals("uuid", bean.uuid)
        assertEquals("password", bean.password)
        assertEquals("sni.example.com", bean.sni)
        assertEquals("sha256value", bean.pinSHA256)
    }

    @Test
    fun `parseJuicity should use default port 443 when not specified`() {
        val bean = parseJuicity(FmtTestConstant.JUICITY_DEFAULT_PORT_URL)

        assertEquals(443, bean.serverPort)
    }

    @Test
    fun `toUri should preserve serializable fields through parseJuicity`() {
        val source = JuicityBean().apply {
            serverAddress = "example.com"
            serverPort = 8443
            uuid = "test-uuid"
            password = "secret"
            sni = "sni.example.com"
            allowInsecure = true
            pinSHA256 = "sha256hash"
        }

        val parsed = parseJuicity(source.toUri())

        assertEquals(source.serverAddress, parsed.serverAddress)
        assertEquals(source.serverPort, parsed.serverPort)
        assertEquals(source.uuid, parsed.uuid)
        assertEquals(source.password, parsed.password)
        assertEquals(source.sni, parsed.sni)
        assertEquals(source.pinSHA256, parsed.pinSHA256)
    }

    @Test
    fun `buildJuicityConfig should emit every configured field`() {
        DataStore.logLevel.setBlocking(0)

        val bean = JuicityBean().apply {
            serverAddress = "example.com"
            serverPort = 8443
            uuid = "test-uuid"
            password = "secret"
            sni = "sni.example.com"
            allowInsecure = true
            pinSHA256 = "sha256hash"
        }

        val config = bean.buildJuicityConfig(port = 2080, shouldProtect = false).toJsonMapKxs()

        assertEquals("$LOCALHOST4:2080", config["listen"])
        assertEquals("example.com:8443", config["server"])
        assertEquals("test-uuid", config["uuid"])
        assertEquals("secret", config["password"])
        assertEquals("sni.example.com", config["sni"])
        assertEquals(true, config["allow_insecure"])
        assertEquals("bbr", config["congestion_control"])
        assertEquals("sha256hash", config["pinned_certchain_sha256"])
        assertEquals("error", config["log_level"])
    }

    @Test
    fun `buildJuicityConfig should drop optional fields instead of emitting null`() {
        DataStore.logLevel.setBlocking(0)

        val bean = JuicityBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "uuid"
            password = "pass"
            sni = ""
            allowInsecure = false
            pinSHA256 = ""
        }

        val config = bean.buildJuicityConfig(port = 2080, shouldProtect = false).toJsonMapKxs()

        assertFalse("sni" in config)
        assertFalse("allow_insecure" in config)
        assertFalse("pinned_certchain_sha256" in config)
        assertFalse("protect_path" in config)
    }

    @Test
    fun `buildJuicityConfig should follow the log level preference`() {
        DataStore.logLevel.setBlocking(0)
        val bean = JuicityBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "uuid"
            password = "pass"
        }

        assertEquals(
            "error",
            bean.buildJuicityConfig(port = 2080, shouldProtect = false).toJsonMapKxs()["log_level"],
        )

        DataStore.logLevel.setBlocking(3)

        assertEquals(
            "debug",
            bean.buildJuicityConfig(port = 2080, shouldProtect = false).toJsonMapKxs()["log_level"],
        )
    }

    @Test
    fun `buildJuicityConfig should wrap an IPv6 server address`() {
        DataStore.logLevel.setBlocking(0)

        val bean = JuicityBean().apply {
            serverAddress = "2001:db8::1"
            serverPort = 8443
            uuid = "uuid"
            password = "pass"
        }

        val config = bean.buildJuicityConfig(port = 2080, shouldProtect = false).toJsonMapKxs()

        assertEquals("[2001:db8::1]:8443", config["server"])
    }

    @Test
    fun `buildSingBoxOutboundJuicityBean should map all fields`() = runTest {
        val bean = JuicityBean().apply {
            serverAddress = "example.com"
            serverPort = 8443
            uuid = "test-uuid"
            password = "secret"
            sni = "sni.example.com"
            allowInsecure = true
            pinSHA256 = "sha256hash"
        }

        val outbound = buildSingBoxOutboundJuicityBean(bean)

        assertEquals(SingBoxOptions.TYPE_JUICITY, outbound.type)
        assertEquals("example.com", outbound.server)
        assertEquals(8443, outbound.server_port)
        assertEquals("test-uuid", outbound.uuid)
        assertEquals("secret", outbound.password)
        assertEquals("sha256hash", outbound.pin_cert_sha256)

        val tls = assertNotNull(outbound.tls)
        assertEquals(true, tls.enabled)
        assertEquals("sni.example.com", tls.server_name)
        assertEquals(true, tls.insecure)
    }

    @Test
    fun `buildSingBoxOutboundJuicityBean should omit pin_cert_sha256 when blank`() = runTest {
        val bean = JuicityBean().apply {
            serverAddress = "example.com"
            serverPort = 443
            uuid = "uuid"
            password = "pass"
            pinSHA256 = ""
        }

        val outbound = buildSingBoxOutboundJuicityBean(bean)

        assertNull(outbound.pin_cert_sha256)
    }

    @Test
    fun `clone should keep the bean a JuicityBean`() {
        val source = JuicityBean().apply {
            serverAddress = "example.com"
            serverPort = 8443
            uuid = "test-uuid"
            password = "secret"
            sni = "sni.example.com"
            allowInsecure = true
            pinSHA256 = "sha256hash"
        }

        val restored = source.clone()

        assertIs<JuicityBean>(restored)
        assertEquals("example.com", restored.serverAddress)
        assertEquals(8443, restored.serverPort)
        assertEquals("test-uuid", restored.uuid)
        assertEquals("secret", restored.password)
        assertEquals("sni.example.com", restored.sni)
        assertEquals(true, restored.allowInsecure)
        assertEquals("sha256hash", restored.pinSHA256)
    }
}
