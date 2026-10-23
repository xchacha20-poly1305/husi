package fr.husi.ui.log

import fr.husi.proto.daemon.LogLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogcatTerminalWriterTest {

    private fun entry(message: String) = LogEntry(LogLevel.INFO, message)

    private fun LogcatTerminalWriter.write(
        logs: List<LogEntry>,
        logLevel: LogLevel = LogLevel.INFO,
        searchQuery: String? = null,
    ) = update(logs, logLevel, searchQuery)

    private val reset = "\u001bc\u001b[3J\u001b[?25l"

    @Test
    fun `first update resets the terminal and writes every entry`() {
        val writer = LogcatTerminalWriter()

        val output = writer.write(listOf(entry("a"), entry("b")))

        assertEquals("${reset}a\r\nb\r\n", output)
    }

    @Test
    fun `appended entries are written alone`() {
        val writer = LogcatTerminalWriter()
        val first = entry("a")
        writer.write(listOf(first))

        val output = writer.write(listOf(first, entry("b")))

        assertEquals("b\r\n", output)
    }

    @Test
    fun `unchanged entries write nothing`() {
        val writer = LogcatTerminalWriter()
        val logs = listOf(entry("a"))
        writer.write(logs)

        assertNull(writer.write(logs.toList()))
    }

    @Test
    fun `trimming the oldest entries still appends`() {
        val writer = LogcatTerminalWriter()
        val first = entry("a")
        val second = entry("b")
        writer.write(listOf(first, second))

        val output = writer.write(listOf(second, entry("c")))

        assertEquals("c\r\n", output)
    }

    @Test
    fun `an equal entry that was not written is not mistaken for the last written one`() {
        val writer = LogcatTerminalWriter()
        writer.write(listOf(entry("same")))

        val output = writer.write(listOf(entry("same")))

        assertEquals("${reset}same\r\n", output)
    }

    @Test
    fun `changing the log level rewrites every entry`() {
        val writer = LogcatTerminalWriter()
        val first = entry("a")
        val second = entry("b")
        writer.write(listOf(second), logLevel = LogLevel.WARN)

        val output = writer.write(listOf(first, second))

        assertEquals("${reset}a\r\nb\r\n", output)
    }

    @Test
    fun `clearing resets the terminal and later entries append`() {
        val writer = LogcatTerminalWriter()
        writer.write(listOf(entry("a")))

        assertEquals(reset, writer.write(emptyList()))
        assertEquals("b\r\n", writer.write(listOf(entry("b"))))
    }

    @Test
    fun `line feeds inside a message become terminal newlines`() {
        val writer = LogcatTerminalWriter()

        val output = writer.write(listOf(entry("a\nb")))

        assertEquals("${reset}a\r\nb\r\n", output)
    }

    @Test
    fun `changing the search query rewrites every entry with the matches reversed`() {
        val writer = LogcatTerminalWriter()
        val first = entry("dial tcp")
        writer.write(listOf(first))

        val output = writer.write(listOf(first), searchQuery = "tcp")

        assertEquals("${reset}dial \u001b[7mtcp\u001b[27m\r\n", output)
    }

    @Test
    fun `highlight leaves a message without a query unchanged`() {
        assertEquals("a\u001b[31mb", highlight("a\u001b[31mb", null))
        assertEquals("a\u001b[31mb", highlight("a\u001b[31mb", ""))
    }

    @Test
    fun `highlight ignores case and marks every match`() {
        assertEquals(
            "\u001b[7mTCP\u001b[27m and \u001b[7mtcp\u001b[27m",
            highlight("TCP and tcp", "tcp"),
        )
    }

    @Test
    fun `highlight does not match inside escape sequences`() {
        assertEquals("\u001b[31mred\u001b[0m", highlight("\u001b[31mred\u001b[0m", "31"))
    }

    @Test
    fun `highlight matches across a color change and keeps reverse video through a reset`() {
        assertEquals(
            "\u001b[31m\u001b[7mre\u001b[0m\u001b[7md\u001b[27m!",
            highlight("\u001b[31mre\u001b[0md!", "red"),
        )
    }

    @Test
    fun `highlight closes reverse video before a sequence that follows the match`() {
        assertEquals(
            "\u001b[7mab\u001b[27m\u001b[0mc",
            highlight("ab\u001b[0mc", "ab"),
        )
    }
}
