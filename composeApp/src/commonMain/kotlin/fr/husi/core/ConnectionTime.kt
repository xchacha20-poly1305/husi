package fr.husi.core

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

fun formatConnectionTime(millis: Long): String {
    if (millis <= 0L) return ""
    val dateTime = Instant.fromEpochMilliseconds(millis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return CONNECTION_TIME_FORMAT.format(dateTime)
}

private val CONNECTION_TIME_FORMAT = LocalDateTime.Format {
    // yyyy-MM-dd HH:mm:ss
    year()
    char('-')
    monthNumber()
    char('-')
    day()
    char(' ')
    hour()
    char(':')
    minute()
    char(':')
    second()
}
