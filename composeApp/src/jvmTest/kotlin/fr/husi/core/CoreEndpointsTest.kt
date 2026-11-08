package fr.husi.core

import io.github.xchacha20_poly1305.kurpc.Status
import io.github.xchacha20_poly1305.kurpc.Transport
import java.io.File
import javax.net.ssl.SSLSocketFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreEndpointsTest {

    /** Building a config must not read the roots: they are read again on every dial. */
    private val trustOnlyWhenDialing: () -> SSLSocketFactory = { error("trust asked before dialing") }

    @Test
    fun `local base path is a directory holding api sock`() {
        val dir = File("husi", "core").path
        assertEquals(Transport.Unix(File(dir, "api.sock").path), localCoreTransport(dir))
    }

    @Test
    fun `a Windows pipe path is dialed as is`() {
        val pipe = """\\.\PIPE\ProtectedPrefix\Administrators\husi"""
        assertEquals(Transport.WindowsNamedPipe(pipe), localCoreTransport(pipe))
    }

    @Test
    fun `remote http defaults to port 80 without TLS`() {
        assertEquals(RemoteCoreAddress("example.com", 80, tls = false), remoteCoreAddress("http://example.com"))
        val config = remoteCoreChannelConfig("http://example.com", "", trustOnlyWhenDialing)
        assertEquals(Transport.Tcp("example.com", 80), config.transport)
        assertEquals("example.com:80", config.authority)
        assertFalse(config.secure)
        assertNull(config.metadata.text("authorization"))
        assertEquals("en", config.metadata.text("accept-language"))
    }

    @Test
    fun `remote https runs TLS in the transport and sends the secret`() {
        assertEquals(
            RemoteCoreAddress("example.com", 8443, tls = true),
            remoteCoreAddress("https://example.com:8443/ignored"),
        )
        val config = remoteCoreChannelConfig("https://example.com:8443/ignored", "s3cret", trustOnlyWhenDialing)
        assertIs<Transport.Custom>(config.transport)
        assertTrue(config.secure)
        assertEquals("example.com:8443", config.authority)
        assertEquals("Bearer s3cret", config.metadata.text("authorization"))
    }

    @Test
    fun `remote IPv6 literal keeps brackets only in the authority`() {
        val address = remoteCoreAddress("https://[::1]")
        assertEquals(RemoteCoreAddress("::1", 443, tls = true), address)
        assertEquals("[::1]:443", address.authority)
    }

    @Test
    fun `remote rejects other schemes and missing hosts`() {
        for (url in listOf("ftp://example.com", "http://", "not a url")) {
            val error = assertFailsWith<CoreRpcException>(url) { remoteCoreAddress(url) }
            assertEquals(Status.Code.INVALID_ARGUMENT, error.code)
        }
    }

    @Test
    fun `health status is field 1 and defaults to UNKNOWN`() {
        assertEquals(1, healthCheckStatus(byteArrayOf(0x08, 0x01)))
        assertEquals(0, healthCheckStatus(ByteArray(0)))
        // An unknown length-delimited field before the status is skipped.
        assertEquals(2, healthCheckStatus(byteArrayOf(0x12, 0x02, 0x61, 0x62, 0x08, 0x02)))
    }

    @Test
    fun `health status rejects a truncated response`() {
        for (response in listOf(byteArrayOf(0x08), byteArrayOf(0x12, 0x05, 0x61), byteArrayOf(0x0B))) {
            val error = assertFailsWith<CoreRpcException> { healthCheckStatus(response) }
            assertEquals(Status.Code.INTERNAL, error.code)
        }
    }
}
