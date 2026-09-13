package fr.husi.ui.jsoneditor

import fr.husi.libcore.Libcore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.measureTime

class ConfigEditPerformanceTest {

    private val frameBudget = 16.milliseconds
    private val editorText = largeConfig(outbounds = 80)

    @Test
    fun `lexing a large config stays within a frame budget`() {
        val document = ConfigJsonDocument.parse(editorText)
        assertTrue(document.lineCount > 700, "expected a large sample, got ${document.lineCount} lines")

        val elapsed = median(repeats = 20) { ConfigJsonDocument.parse(editorText) }

        assertTrue(elapsed < frameBudget, "lexing ${document.lineCount} lines took $elapsed")
    }

    @Test
    fun `highlighting styles only the visible window`() {
        val document = ConfigJsonDocument.parse(editorText)
        val lineHeight = 40
        val window = highlightedLineRange(
            scrollOffsetPx = 0,
            viewportHeightPx = 30 * lineHeight,
            lineHeightPx = lineHeight,
        )

        val styled = document.tokensInLines(window)

        assertTrue(
            styled.size * 4 < document.tokens.size,
            "windowed highlighting styled ${styled.size} of ${document.tokens.size} tokens",
        )
        assertTrue(styled.isNotEmpty(), "the visible window must still be highlighted")
    }

    @Test
    fun `the highlight window covers every visible line`() {
        val lineHeight = 40
        val scrollOffset = 300 * lineHeight
        val window = highlightedLineRange(
            scrollOffsetPx = scrollOffset,
            viewportHeightPx = 30 * lineHeight,
            lineHeightPx = lineHeight,
        )

        assertTrue(window.first <= 300, "window $window starts after the first visible line")
        assertTrue(window.last >= 330, "window $window ends before the last visible line")
    }

    @Test
    fun `scrolling inside a chunk keeps the highlight window stable`() {
        val lineHeight = 40
        val viewport = 30 * lineHeight
        val first = highlightedLineRange(0, viewport, lineHeight)
        val nudged = highlightedLineRange(lineHeight, viewport, lineHeight)
        val nextChunk = highlightedLineRange(highlightChunkLines * lineHeight, viewport, lineHeight)

        assertEquals(first, nudged)
        assertTrue(nextChunk != first, "crossing a chunk boundary must move the window")
    }

    @Test
    fun `schema completion in a large config stays within a frame budget`() {
        val completer = ConfigSchemaCompleter(
            Json.parseToJsonElement(Libcore.generateConfigSchema()).jsonObject,
        )
        val cursor = editorText.indexOf("\"method\"", startIndex = editorText.length / 2) + 3
        assertTrue(cursor > 3, "the sample must contain a deeply nested key")

        val elapsed = median(repeats = 10) { completer.complete(editorText, cursor) }

        assertTrue(elapsed < frameBudget, "completing a deeply nested key took $elapsed")
    }

    private fun median(repeats: Int, block: () -> Unit): Duration {
        repeat(repeats) { block() }
        val samples = List(repeats) { measureTime(block) }
        return samples.sorted()[repeats / 2]
    }

    private fun largeConfig(outbounds: Int): String = buildString {
        appendLine("{")
        appendLine("""  "log": { "level": "info", "timestamp": true },""")
        appendLine("""  "outbounds": [""")
        repeat(outbounds) { index ->
            appendLine("    {")
            appendLine("""      "type": "shadowsocks",""")
            appendLine("""      "tag": "proxy-$index",""")
            appendLine("""      "server": "192.168.1.$index",""")
            appendLine("""      "server_port": ${10000 + index},""")
            appendLine("""      "method": "aes-256-gcm",""")
            appendLine("""      "password": "secret-$index",""")
            appendLine("""      "multiplex": { "enabled": true, "max_streams": 8 }""")
            appendLine(if (index == outbounds - 1) "    }" else "    },")
        }
        appendLine("  ]")
        append("}")
    }
}
