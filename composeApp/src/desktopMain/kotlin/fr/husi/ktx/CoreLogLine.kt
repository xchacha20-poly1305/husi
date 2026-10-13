package fr.husi.ktx

import fr.husi.proto.daemon.LogLevel

private val ansiEscape = Regex("\u001B\\[[0-9;]*m")

// sing-box prints "LEVEL[0012] ", "LEVEL " or "-0700 2006-01-02 15:04:05 LEVEL " before the message.
private val coreLogLinePrefix = Regex(
    "^(?:[+-]\\d{4} \\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2} )?" +
        "(PANIC|FATAL|ERROR|WARN|INFO|DEBUG|TRACE)(?=[\\[ ])",
)

/**
 * Reads the level husi-core printed at the start of [line], or null for a line it printed
 * without one, such as a Go panic and its stack trace.
 */
internal fun coreLogLineLevel(line: String): LogLevel? {
    val plainLine = line.replace(ansiEscape, "")
    val levelName = coreLogLinePrefix.find(plainLine)?.groupValues?.get(1) ?: return null
    return LogLevel.valueOf(levelName)
}
