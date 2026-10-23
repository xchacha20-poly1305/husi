package fr.husi.ui.log

import fr.husi.proto.daemon.LogLevel

internal class LogcatTerminalWriter {
    private var written = false
    private var logLevel: LogLevel? = null
    private var searchQuery: String? = null
    private var lastWritten: LogEntry? = null

    /**
     * Returns what brings the terminal from what it shows to [logs], or null when it already shows
     * them. [logs] are the entries [logLevel] and [searchQuery] let through; text matching
     * [searchQuery] is shown in reverse video.
     */
    fun update(logs: List<LogEntry>, logLevel: LogLevel, searchQuery: String?): String? {
        val sameFilter = written && logLevel == this.logLevel && searchQuery == this.searchQuery
        val appendFrom = if (sameFilter) appendStart(logs) else null
        written = true
        this.logLevel = logLevel
        this.searchQuery = searchQuery
        if (appendFrom == logs.size) return null
        lastWritten = logs.lastOrNull()
        return buildString {
            if (appendFrom == null) append(RESET)
            for (index in (appendFrom ?: 0) until logs.size) {
                append(highlight(logs[index].message, searchQuery).replace("\n", NEWLINE))
                append(NEWLINE)
            }
        }
    }

    /**
     * Where the entries after the last written one start, or null when that entry is gone.
     * Trimming the oldest entries keeps it; clearing the log or changing the filter drops it.
     */
    private fun appendStart(logs: List<LogEntry>): Int? {
        val last = lastWritten ?: return 0
        // Compared by identity: two lines with the same text are still different entries.
        for (index in logs.indices.reversed()) {
            if (logs[index] === last) {
                return index + 1
            }
        }
        return null
    }

    private companion object {
        const val NEWLINE = "\r\n"

        /** Full reset, erase the scrollback, and hide the cursor, which a log has no use for. */
        const val RESET = "\u001bc\u001b[3J\u001b[?25l"
    }
}

private const val ESCAPE = '\u001b'
private const val REVERSE_ON = "\u001b[7m"
private const val REVERSE_OFF = "\u001b[27m"
private val CONTROL_SEQUENCE_FINAL = '@'..'~'

/**
 * Wraps each case-insensitive match of [query] in [message] in reverse video. Matching looks only
 * at the visible text, so a match may span the escape sequences that color the message.
 */
internal fun highlight(message: String, query: String?): String {
    if (query.isNullOrEmpty()) return message
    val sequences = escapeSequences(message)
    val visible = buildString {
        var start = 0
        for (sequence in sequences) {
            append(message, start, sequence.first)
            start = sequence.last + 1
        }
        append(message, start, message.length)
    }
    val matches = buildList {
        var start = visible.indexOf(query, ignoreCase = true)
        while (start >= 0) {
            add(start until start + query.length)
            start = visible.indexOf(query, start + query.length, ignoreCase = true)
        }
    }
    if (matches.isEmpty()) return message

    return buildString {
        var visibleIndex = 0
        var matchIndex = 0
        var reversed = false
        var sequenceIndex = 0
        var index = 0
        while (index < message.length) {
            if (reversed && visibleIndex > matches[matchIndex].last) {
                append(REVERSE_OFF)
                reversed = false
                matchIndex++
            }
            val sequence = sequences.getOrNull(sequenceIndex)
            if (sequence != null && sequence.first == index) {
                append(message, sequence.first, sequence.last + 1)
                // The sequence may be a reset that also ended the reverse video.
                if (reversed) append(REVERSE_ON)
                sequenceIndex++
                index = sequence.last + 1
                continue
            }
            if (!reversed && matchIndex < matches.size && visibleIndex == matches[matchIndex].first) {
                append(REVERSE_ON)
                reversed = true
            }
            append(message[index])
            visibleIndex++
            index++
        }
        if (reversed) append(REVERSE_OFF)
    }
}

/**
 * The index ranges of the escape sequences in [text]: control sequences (`ESC [` up to a final
 * byte) and, for anything else, the escape and the character after it.
 */
private fun escapeSequences(text: String): List<IntRange> = buildList {
    var index = 0
    while (index < text.length) {
        if (text[index] != ESCAPE) {
            index++
            continue
        }
        val start = index
        index++
        if (index < text.length && text[index] == '[') {
            index++
            while (index < text.length && text[index] !in CONTROL_SEQUENCE_FINAL) {
                index++
            }
        }
        // Takes the final byte, or the single character after a bare escape.
        index = minOf(index + 1, text.length)
        add(start until index)
    }
}
