package fr.husi.core

import com.squareup.wire.GrpcStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoreEndpointsTest {

    @Test
    fun `local base path is a directory holding api sock`() {
        val dir = File("husi", "core").path
        assertEquals(File(dir, "api.sock").path, localCoreSocketPath(dir))
    }

    @Test
    fun `a Windows pipe path is dialed as is`() {
        val pipe = """\\.\PIPE\ProtectedPrefix\Administrators\husi"""
        assertEquals(pipe, localCoreSocketPath(pipe))
    }

    @Test
    fun `remote http defaults to port 80 without TLS`() {
        assertEquals(RemoteCoreAddress("example.com", 80, tls = false), remoteCoreAddress("http://example.com"))
    }

    @Test
    fun `remote https parses its host and port`() {
        assertEquals(
            RemoteCoreAddress("example.com", 8443, tls = true),
            remoteCoreAddress("https://example.com:8443/ignored"),
        )
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
            assertEquals(GrpcStatus.INVALID_ARGUMENT, error.code)
        }
    }
}
