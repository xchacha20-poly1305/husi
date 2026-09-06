package fr.husi.ktx

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * DisplayTime are formats for display, which can change anytime.
 */
object DisplayTime {

    private val DISPLAY_DATE_TIME = LocalDateTime.Format {
        // yyyy-MM-dd HH:mm
        year()
        char('-')
        monthNumber()
        char('-')
        day()
        char(' ')
        hour()
        char(':')
        minute()
    }

    fun dateTime(instant: Instant): String {
        return DISPLAY_DATE_TIME.format(instant.toLocalDateTime(TimeZone.currentSystemDefault()))
    }

    fun date(instant: Instant): String {
        val date = instant.toLocalDateTime(TimeZone.currentSystemDefault()).date
        return LocalDate.Formats.ISO.format(date)
    }

    private val DISPLAY_UTC_DATE_TIME = LocalDateTime.Format {
        // yyyy-MM-dd HH:mm UTC
        dateTime(DISPLAY_DATE_TIME)
        chars(" UTC")
    }

    fun utcDateTime(instant: Instant): String {
        return DISPLAY_UTC_DATE_TIME.format(instant.toLocalDateTime(TimeZone.UTC))
    }

}

private val FILE_NAME_TIMESTAMP = LocalDateTime.Format {
    // yyyy-MM-dd_HH-mm-ss
    year()
    char('-')
    monthNumber()
    char('-')
    day()
    char('_')
    hour()
    char('-')
    minute()
    char('-')
    second()
}

fun fileNameTimestamp(instant: Instant = Clock.System.now()): String {
    return FILE_NAME_TIMESTAMP.format(instant.toLocalDateTime(TimeZone.currentSystemDefault()))
}
