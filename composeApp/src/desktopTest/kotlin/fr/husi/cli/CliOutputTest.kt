package fr.husi.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CliOutputTest {
    @Test
    fun `stripColors removes CSI sequences`() {
        assertEquals("hello world", stripColors("hello \u001B[31mworld\u001B[0m"))
    }

    @Test
    fun `stripColors returns a plain string unchanged`() {
        val plain = "hello world"
        assertEquals(plain, stripColors(plain))
    }

    @Test
    fun `stripColors keeps a truncated escape`() {
        val truncated = "hello\u001B[31"
        assertEquals(truncated, stripColors(truncated))
    }

    @Test
    fun `displayWidth counts ASCII as one`() {
        assertEquals(3, displayWidth("abc"))
    }

    @Test
    fun `displayWidth counts CJK as two`() {
        assertEquals(2, displayWidth("中"))
        assertEquals(4, displayWidth("中文"))
    }

    @Test
    fun `displayWidth ignores combining marks`() {
        assertEquals(1, displayWidth("e\u0301"))
    }

    @Test
    fun `displayWidth counts emoji as two`() {
        assertEquals(2, displayWidth("🥺"))
    }

    @Test
    fun `TableWriter non-terminal is tab-joined without a header`() {
        val table = TableWriter(
            header = listOf("TAG", "TYPE"),
            emptyMessage = "no rows",
            terminal = false,
        )
        table.addRow("proxy", "socks")
        table.addRow("", "direct")

        assertNull(table.renderHeader())
        assertNull(table.renderEmptyMessage())
        assertEquals("proxy\tsocks\n-\tdirect\n", table.renderRows())
    }

    @Test
    fun `TableWriter terminal pads columns except the last`() {
        val table = TableWriter(
            header = listOf("TAG", "TYPE"),
            terminal = true,
        )
        table.addRow("中", "proxy")

        assertEquals("TAG  TYPE", table.renderHeader())
        assertEquals("中   proxy\n", table.renderRows())
    }

    @Test
    fun `BlockWriter aligns values to the longest label plus three`() {
        val block = BlockWriter()
        block.addLine("State", "started")
        block.addLine("Goroutines", "12")

        assertEquals(
            "State:       started\nGoroutines:  12\n",
            block.render(),
        )
    }

    @Test
    fun `BlockWriter substitutes a dash for an empty value`() {
        val block = BlockWriter()
        block.addLine("Uptime", "")

        assertEquals("Uptime:  -\n", block.render())
    }

    @Test
    fun `formatApiTime drops fractional seconds`() {
        val withMillis = formatApiTime(1755260000123L)
        val wholeSecond = formatApiTime(1755260000000L)

        // Go's RFC3339 layout never prints a fraction, so the two must render identically.
        assertEquals(wholeSecond, withMillis)
        assertFalse(withMillis.contains('.'), "unexpected fractional seconds in $withMillis")
    }

    @Test
    fun `formatApiTime renders a zero timestamp as empty`() {
        assertEquals("", formatApiTime(0L))
    }

    @Test
    fun `formatGoDuration seconds only`() {
        assertEquals("45s", formatGoDuration(45.seconds))
        assertEquals("0s", formatGoDuration(0.seconds))
    }

    @Test
    fun `formatGoDuration minutes`() {
        assertEquals("2m3s", formatGoDuration(2.minutes + 3.seconds))
        assertEquals("1m0s", formatGoDuration(1.minutes))
    }

    @Test
    fun `formatGoDuration hours`() {
        assertEquals("1h2m3s", formatGoDuration(1.hours + 2.minutes + 3.seconds))
        assertEquals("1h0m0s", formatGoDuration(1.hours))
    }
}
