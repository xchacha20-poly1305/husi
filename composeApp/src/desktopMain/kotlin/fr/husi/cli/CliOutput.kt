package fr.husi.cli

/**
 * [java.io.Console.isTerminal] only exists in Java 22+.
 */
val isTerminal: Boolean by lazy {
    val console = System.console() ?: return@lazy false
    runCatching {
        console.javaClass.getMethod("isTerminal").invoke(console) as Boolean
    }.getOrDefault(true)
}

fun writeStderrLine(message: String) {
    if (!isTerminal) return
    System.err.println(message)
}

private const val CSI_PREFIX = "\u001B["
private const val EMPTY_CELL = "-"
private const val COLUMN_SEPARATOR = "  "
private const val BLOCK_LABEL_PADDING = 3

internal fun stripColors(message: String): String {
    if (!message.contains(CSI_PREFIX)) {
        return message
    }
    val builder = StringBuilder()
    var start = 0
    var index = 0
    while (index < message.length) {
        if (message[index] != '\u001B' ||
            index + 1 >= message.length ||
            message[index + 1] != '['
        ) {
            index++
            continue
        }
        var end = index + 2
        while (end < message.length && message[end] != 'm') {
            end++
        }
        if (end >= message.length) {
            break
        }
        builder.append(message, start, index)
        index = end + 1
        start = index
    }
    builder.append(message, start, message.length)
    return builder.toString()
}

internal fun displayWidth(text: String): Int {
    var width = 0
    var index = 0
    while (index < text.length) {
        val codePoint = text.codePointAt(index)
        width += codePointWidth(codePoint)
        index += Character.charCount(codePoint)
    }
    return width
}

internal class TableWriter(
    private val header: List<String>,
    private val emptyMessage: String = "",
    private val terminal: Boolean = isTerminal,
) {
    private val rows = mutableListOf<List<String>>()

    fun addRow(vararg cells: String) {
        rows += cells.map { cell -> cell.ifEmpty { EMPTY_CELL } }
    }

    fun renderHeader(): String? {
        if (rows.isEmpty() || !terminal) return null
        return renderPaddedRow(header, columnWidths())
    }

    fun renderRows(): String {
        if (rows.isEmpty()) return ""
        if (!terminal) {
            return buildString {
                for (row in rows) {
                    append(row.joinToString("\t"))
                    append('\n')
                }
            }
        }
        val widths = columnWidths()
        return buildString {
            for (row in rows) {
                append(renderPaddedRow(row, widths))
                append('\n')
            }
        }
    }

    fun renderEmptyMessage(): String? = if (rows.isEmpty()) emptyMessage else null

    fun flush() {
        if (rows.isEmpty()) {
            writeStderrLine(emptyMessage)
            return
        }
        if (!terminal) {
            print(renderRows())
            return
        }
        writeStderrLine(renderHeader().orEmpty())
        print(renderRows())
    }

    private fun columnWidths(): IntArray {
        val widths = IntArray(header.size) { index -> displayWidth(header[index]) }
        for (row in rows) {
            for (index in row.indices) {
                widths[index] = maxOf(widths[index], displayWidth(row[index]))
            }
        }
        return widths
    }

    /**
     * @param widths is computed once per render pass: measuring it per row is quadratic.
     * */
    private fun renderPaddedRow(cells: List<String>, widths: IntArray): String {
        return buildString {
            for (index in cells.indices) {
                if (index > 0) append(COLUMN_SEPARATOR)
                val cell = cells[index]
                append(cell)
                if (index < cells.lastIndex) {
                    append(" ".repeat(widths[index] - displayWidth(cell)))
                }
            }
        }
    }
}

internal class BlockWriter {
    private val lines = mutableListOf<BlockLine>()

    fun addLine(label: String, value: String) {
        lines += BlockLine(label, value.ifEmpty { EMPTY_CELL })
    }

    fun render(): String {
        if (lines.isEmpty()) return ""
        val labelWidth = lines.maxOf { it.label.length } + BLOCK_LABEL_PADDING
        return buildString {
            for ((label, value) in lines) {
                append(label)
                append(':')
                append(" ".repeat(labelWidth - label.length - 1))
                append(value)
                append('\n')
            }
        }
    }

    fun flush() {
        print(render())
    }
}

private data class BlockLine(val label: String, val value: String)

private val ZERO_WIDTH_RANGES = listOf(
    0x200B..0x200F,
    0xFEFF..0xFEFF,
)

// Unicode East Asian Width W/F, as approximated by go-runewidth.
private val WIDE_RANGES = listOf(
    0x1100..0x115F,
    0x2E80..0x303E,
    0x3041..0x33FF,
    0x3400..0x4DBF,
    0x4E00..0x9FFF,
    0xA000..0xA4CF,
    0xAC00..0xD7A3,
    0xF900..0xFAFF,
    0xFE10..0xFE19,
    0xFE30..0xFE6F,
    0xFF00..0xFF60,
    0xFFE0..0xFFE6,
    0x1F300..0x1F64F,
    0x1F900..0x1F9FF,
    0x20000..0x3FFFD,
)

private fun codePointWidth(codePoint: Int): Int {
    val type = Character.getType(codePoint)
    if (type == Character.NON_SPACING_MARK.toInt() || type == Character.ENCLOSING_MARK.toInt()) {
        return 0
    }
    if (ZERO_WIDTH_RANGES.any { codePoint in it }) return 0
    if (WIDE_RANGES.any { codePoint in it }) return 2
    return 1
}
