package fr.husi.ktx

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(FormatStringsInDatetimeFormats::class)
private val fileNameDateTimeFormat = LocalDateTime.Format {
    byUnicodePattern("yyyy-MM-dd_HH-mm-ss")
}

@OptIn(FormatStringsInDatetimeFormats::class)
private val displayDateTimeFormat = LocalDateTime.Format {
    byUnicodePattern("yyyy-MM-dd HH:mm")
}

private fun nowIn(timeZone: TimeZone): LocalDateTime = Clock.System.now().toLocalDateTime(timeZone)

fun currentFileNameTimestamp(): String {
    return fileNameDateTimeFormat.format(nowIn(TimeZone.currentSystemDefault()))
}

fun currentBackupFileTimestamp(): String {
    return currentFileNameTimestamp()
}

fun currentUtcReportTimestamp(): String {
    return displayDateTimeFormat.format(nowIn(TimeZone.UTC)) + " UTC"
}

fun formatLocalDateTime(epochSeconds: Long): String {
    val dateTime = Instant.fromEpochSeconds(epochSeconds)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return displayDateTimeFormat.format(dateTime)
}

fun formatDate(millis: Long): String {
    val date = Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
    return LocalDate.Formats.ISO.format(date)
}
