package fr.husi.ktx

import fr.husi.proto.daemon.LogLevel

private const val ESCAPE = '\u001B'
private const val CONTROL_SEQUENCE_START = '['
private const val COLOR_SEQUENCE_END = 'm'
private const val UTC_OFFSET_LENGTH = 5

// "-0700 2006-01-02 15:04:05 LEVEL message" splits into offset, date, time and the rest.
private const val FULL_TIMESTAMP_PARTS = 4

/**
 * Reads the level husi-core printed at the start of [line], or null for a line it printed
 * without one, such as a Go panic and its stack trace.
 *
 * sing-box prints "LEVEL[0012] ", "LEVEL " or "-0700 2006-01-02 15:04:05 LEVEL " before the
 * message, with the level possibly wrapped in color codes.
 */
internal fun coreLogLineLevel(line: String): LogLevel? {
    val plainLine = stripColors(line)
    return levelAtStart(plainLine) ?: dropFullTimestamp(plainLine)?.let(::levelAtStart)
}

private fun levelAtStart(text: String): LogLevel? {
    val levelEnd = text.indexOfFirst { it == '[' || it == ' ' }
    if (levelEnd <= 0) return null
    val levelName = text.substring(0, levelEnd)
    return LogLevel.entries.firstOrNull { it != LogLevel.UNRECOGNIZED && it.name == levelName }
}

private fun dropFullTimestamp(text: String): String? {
    val parts = text.split(' ', limit = FULL_TIMESTAMP_PARTS)
    if (parts.size < FULL_TIMESTAMP_PARTS) return null
    val utcOffset = parts[0]
    val isUtcOffset = utcOffset.length == UTC_OFFSET_LENGTH &&
        (utcOffset[0] == '+' || utcOffset[0] == '-') &&
        utcOffset.drop(1).all(Char::isDigit)
    return if (isUtcOffset) parts.last() else null
}

private fun stripColors(text: String): String {
    if (ESCAPE !in text) return text
    val builder = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        if (text[index] == ESCAPE && text.getOrNull(index + 1) == CONTROL_SEQUENCE_START) {
            val sequenceEnd = text.indexOf(COLOR_SEQUENCE_END, index + 2)
            if (sequenceEnd >= 0) {
                index = sequenceEnd + 1
                continue
            }
        }
        builder.append(text[index])
        index++
    }
    return builder.toString()
}
