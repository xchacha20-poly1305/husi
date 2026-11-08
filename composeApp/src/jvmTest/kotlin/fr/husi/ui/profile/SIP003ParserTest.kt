package fr.husi.ui.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SIP003ParserTest {

    @Test
    fun `parseSIP003 returns empty map for empty input`() {
        assertTrue(parseSIP003("").isEmpty())
    }

    @Test
    fun `parseSIP003 parses a single key-value pair`() {
        assertEquals(mapOf("obfs" to "http"), parseSIP003("obfs=http"))
    }

    @Test
    fun `parseSIP003 parses multiple entries separated by semicolon`() {
        assertEquals(
            mapOf("obfs" to "http", "obfs-host" to "example.com"),
            parseSIP003("obfs=http;obfs-host=example.com"),
        )
    }

    @Test
    fun `parseSIP003 treats a key without equals as a flag with value 1`() {
        assertEquals(mapOf("tls" to "1"), parseSIP003("tls"))
    }

    @Test
    fun `parseSIP003 treats a flag mixed with normal entries as value 1`() {
        assertEquals(
            mapOf("tls" to "1", "mode" to "websocket"),
            parseSIP003("tls;mode=websocket"),
        )
    }

    @Test
    fun `parseSIP003 treats key with empty value as empty string not flag`() {
        assertEquals(mapOf("tls" to ""), parseSIP003("tls="))
    }

    @Test
    fun `parseSIP003 unescapes backslash-escaped separators`() {
        assertEquals(
            mapOf("k" to "a;b=c\\d"),
            parseSIP003("""k=a\;b\=c\\d"""),
        )
    }

    @Test
    fun `parseSIP003 keeps last value when key repeats`() {
        // Sing-box behavior: Args.Get returns the first, but the parser appends; we
        // overwrite in the editor's flat map because Args.Get([0]) preserves first-write order.
        // This test pins our flat-map semantics — last write wins.
        assertEquals(mapOf("k" to "second"), parseSIP003("k=first;k=second"))
    }

    @Test
    fun `parseSIP003 ignores stray trailing semicolon`() {
        assertEquals(mapOf("obfs" to "http"), parseSIP003("obfs=http;"))
    }

    @Test
    fun `serializeSIP003 emits key equals value separated by semicolons`() {
        assertEquals(
            "obfs=http;obfs-host=example.com",
            serializeSIP003(
                listOf(
                    "obfs" to "http",
                    "obfs-host" to "example.com",
                ),
            ),
        )
    }

    @Test
    fun `serializeSIP003 omits null and empty values`() {
        assertEquals(
            "host=h",
            serializeSIP003(
                listOf(
                    "tls" to null,
                    "host" to "h",
                    "path" to "",
                ),
            ),
        )
    }

    @Test
    fun `serializeSIP003 escapes special characters in values`() {
        assertEquals(
            """k=a\;b\=c\\d""",
            serializeSIP003(listOf("k" to "a;b=c\\d")),
        )
    }

    @Test
    fun `parse then serialize round-trips a non-trivial string`() {
        val original = """k=a\;b\=c\\d;mode=websocket"""
        val map = parseSIP003(original)
        val rebuilt = serializeSIP003(map.entries.map { it.key to it.value })
        assertEquals(original, rebuilt)
    }
}
