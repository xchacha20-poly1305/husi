package fr.husi.ui

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteSettingsSelectedLineTest {

    @Test
    fun `selectedLine returns current single line`() {
        val line = "set:geoip-cn".selectedLine(cursor = 4)

        requireNotNull(line)
        assertEquals(0, line.start)
        assertEquals(12, line.end)
        assertEquals(4, line.cursor)
        assertEquals("set:geoip-cn", line.raw)
        assertEquals("set:geoip-cn", line.text)
        assertEquals("", line.suffix)
    }

    @Test
    fun `selectedLine returns line before newline when cursor is at newline`() {
        val line = "set:foo\nset:bar".selectedLine(cursor = 7)

        requireNotNull(line)
        assertEquals(0, line.start)
        assertEquals(7, line.end)
        assertEquals("set:foo", line.text)
    }

    @Test
    fun `selectedLine returns empty line for consecutive newlines`() {
        val text = "set:foo\n\nset:bar"

        val line = text.selectedLine(cursor = 8)

        requireNotNull(line)
        assertEquals(8, line.start)
        assertEquals(8, line.end)
        assertEquals("", line.raw)
        assertEquals("", line.text)
    }

    @Test
    fun `selectedLine returns second empty line in three consecutive lines`() {
        val text = "a\n\n\nb"

        val line = text.selectedLine(cursor = 3)

        requireNotNull(line)
        assertEquals(3, line.start)
        assertEquals(3, line.end)
        assertEquals("", line.raw)
    }

    @Test
    fun `selectedLine clamps cursor beyond text length`() {
        val line = "set:foo".selectedLine(cursor = 99)

        requireNotNull(line)
        assertEquals(7, line.cursor)
        assertEquals(0, line.start)
        assertEquals(7, line.end)
        assertEquals("set:foo", line.text)
    }

    @Test
    fun `selectedLine strips carriage return from text but keeps suffix`() {
        val line = "set:foo\r\nset:bar".selectedLine(cursor = 8)

        requireNotNull(line)
        assertEquals("set:foo\r", line.raw)
        assertEquals("set:foo", line.text)
        assertEquals("\r", line.suffix)
    }

    @Test
    fun `selectedLine returns null for range selection`() {
        assertNull(selectedLine("set:foo\nset:bar", TextRange(2, 5)))
    }

    @Test
    fun `selectedLine on empty string`() {
        val line = "".selectedLine(cursor = 0)

        requireNotNull(line)
        assertEquals(0, line.start)
        assertEquals(0, line.end)
        assertEquals(0, line.cursor)
        assertEquals("", line.raw)
        assertEquals("", line.text)
        assertEquals("", line.suffix)
    }

    @Test
    fun `selectedLine on single newline at cursor zero`() {
        val line = "\n".selectedLine(cursor = 0)

        requireNotNull(line)
        assertEquals(0, line.start)
        assertEquals(0, line.end)
        assertEquals("", line.raw)
    }

    @Test
    fun `selectedLine on single newline at cursor one`() {
        val line = "\n".selectedLine(cursor = 1)

        requireNotNull(line)
        assertEquals(1, line.start)
        assertEquals(1, line.end)
        assertEquals("", line.raw)
    }

    @Test
    fun `selectedLine on text starting with newline at cursor zero`() {
        val line = "\nset:foo".selectedLine(cursor = 0)

        requireNotNull(line)
        assertEquals(0, line.start)
        assertEquals(0, line.end)
        assertEquals("", line.raw)
    }

    @Test
    fun `selectedLine on text starting with newline at second line`() {
        val line = "\nset:foo".selectedLine(cursor = 1)

        requireNotNull(line)
        assertEquals(1, line.start)
        assertEquals(8, line.end)
        assertEquals("set:foo", line.text)
    }

    @Test
    fun `selectedLine after trailing newline`() {
        val line = "set:foo\n".selectedLine(cursor = 8)

        requireNotNull(line)
        assertEquals(8, line.start)
        assertEquals(8, line.end)
        assertEquals("", line.raw)
    }

    @Test
    fun `selectedLine selects middle line`() {
        val line = "a\nbb\nccc".selectedLine(cursor = 3)

        requireNotNull(line)
        assertEquals(2, line.start)
        assertEquals(4, line.end)
        assertEquals(3, line.cursor)
        assertEquals("bb", line.text)
    }

    @Test
    fun `selectedLine at newline boundary selects line before it`() {
        val line = "a\nbb\nccc".selectedLine(cursor = 4)

        requireNotNull(line)
        assertEquals(2, line.start)
        assertEquals(4, line.end)
        assertEquals("bb", line.text)
    }

    @Test
    fun `selectedLine mid-word on last line`() {
        val line = "a\nb\nccc".selectedLine(cursor = 6)

        requireNotNull(line)
        assertEquals(4, line.start)
        assertEquals(7, line.end)
        assertEquals(6, line.cursor)
        assertEquals("ccc", line.text)
    }

    @Test
    fun `selectedLine on CRLF-only text`() {
        val line = "\r\n".selectedLine(cursor = 0)

        requireNotNull(line)
        assertEquals(0, line.start)
        assertEquals(1, line.end)
        assertEquals("\r", line.raw)
        assertEquals("", line.text)
        assertEquals("\r", line.suffix)
    }

    @Test
    fun `selectedLine middle line with CRLF endings`() {
        val line = "a\r\nb\r\nc".selectedLine(cursor = 3)

        requireNotNull(line)
        assertEquals(3, line.start)
        assertEquals(5, line.end)
        assertEquals("b\r", line.raw)
        assertEquals("b", line.text)
        assertEquals("\r", line.suffix)
    }

    @Test
    fun `ruleSetSuggestionPrefix returns content for set rule`() {
        val line = "set:geoip-cn".selectedLine(cursor = 12)

        requireNotNull(line)
        assertEquals("geoip-cn", line.ruleSetSuggestionPrefix())
    }

    @Test
    fun `ruleSetSuggestionPrefix supports dns suffixed set rules`() {
        val plusDnsLine = "set+dns:g".selectedLine(cursor = 9)
        val minusDnsLine = "set-dns:g".selectedLine(cursor = 9)

        requireNotNull(plusDnsLine)
        requireNotNull(minusDnsLine)
        assertEquals("g", plusDnsLine.ruleSetSuggestionPrefix())
        assertEquals("g", minusDnsLine.ruleSetSuggestionPrefix())
    }

    @Test
    fun `ruleSetSuggestionPrefix returns null when cursor is not at line end`() {
        val line = "set:geoip-cn".selectedLine(cursor = 4)

        requireNotNull(line)
        assertNull(line.ruleSetSuggestionPrefix())
    }

    @Test
    fun `ruleSetSuggestionPrefix returns null for non set rules`() {
        val line = "full:example.org".selectedLine(cursor = 16)

        requireNotNull(line)
        assertNull(line.ruleSetSuggestionPrefix())
    }

    @Test
    fun `findRuleSetSuggestions returns bounded prefix matches`() {
        val ruleSets = listOf("apple", "banana", "geoip-cn", "geoip-private", "geosite-google", "youtube")

        assertEquals(
            listOf("geoip-cn", "geoip-private"),
            ruleSets.findRuleSetSuggestions(prefix = "geoip", limit = 2),
        )
    }

    @Test
    fun `findRuleSetSuggestions returns empty list when prefix is absent`() {
        val ruleSets = listOf("apple", "banana", "geoip-cn")

        assertEquals(emptyList(), ruleSets.findRuleSetSuggestions(prefix = "zz", limit = 5))
    }

    @Test
    fun `findRuleSetSuggestions works with empty prefix`() {
        val ruleSets = listOf("apple", "banana", "geoip-cn")

        assertEquals(
            listOf("apple", "banana"),
            ruleSets.findRuleSetSuggestions(prefix = "", limit = 2),
        )
    }

    private fun String.selectedLine(cursor: Int): SelectedLine? {
        return selectedLine(this, TextRange(cursor))
    }
}
