package fr.husi.database

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RemoteServerUrlTest {

    @Test
    fun `missing scheme defaults to http`() {
        assertEquals("http://127.0.0.1:9090", normalizeRemoteServerURL("127.0.0.1:9090"))
        assertEquals("http://example.com:443", normalizeRemoteServerURL(" example.com:443 "))
    }

    @Test
    fun `explicit scheme is kept`() {
        assertEquals("http://127.0.0.1:9090", normalizeRemoteServerURL("http://127.0.0.1:9090"))
        assertEquals("http://example.com:80", normalizeRemoteServerURL("HTTP://example.com:80"))
        assertEquals(
            "https://example.com:443",
            normalizeRemoteServerURL("https://example.com:443"),
        )
    }

    @Test
    fun `port is optional`() {
        assertEquals("http://example.com", normalizeRemoteServerURL("example.com"))
        assertEquals("https://example.com", normalizeRemoteServerURL("https://example.com"))
    }

    @Test
    fun `ipv6 host stays wrapped`() {
        assertEquals("http://[::1]:9090", normalizeRemoteServerURL("[::1]:9090"))
        assertEquals("http://[::1]:9090", normalizeRemoteServerURL("http://[::1]:9090"))
        assertEquals("https://[::1]:9090", normalizeRemoteServerURL("https://[::1]:9090"))
    }

    @Test
    fun `rejects path userinfo query fragment and unknown schemes`() {
        assertNull(normalizeRemoteServerURL(""))
        assertNull(normalizeRemoteServerURL("ftp://example.com:9090"))
        assertNull(normalizeRemoteServerURL("http://example.com:9090/path"))
        assertNull(normalizeRemoteServerURL("http://user:pass@example.com:9090"))
        assertNull(normalizeRemoteServerURL("http://example.com:9090?key=value"))
        assertNull(normalizeRemoteServerURL("http://example.com:9090#name"))
        assertNull(normalizeRemoteServerURL("not a url"))
    }

    @Test
    fun `trailing slash is accepted and dropped`() {
        assertEquals("http://example.com:9090", normalizeRemoteServerURL("http://example.com:9090/"))
    }
}
