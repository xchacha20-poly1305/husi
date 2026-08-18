package fr.husi.core

import fr.husi.ktx.emptyAsNull
import fr.husi.libcore.Libcore
import fr.husi.proto.daemon.Connection
import fr.husi.proto.daemon.ConnectionEvent
import fr.husi.proto.daemon.ConnectionEventType
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** "$name/$type" label composition previously done in Go generateBound. */
fun formatBound(name: String, type: String): String {
    if (name.isEmpty()) return type
    if (type.isEmpty()) return name
    return "$name/$type"
}

fun Connection.inboundLabel(): String = formatBound(inbound, inboundType)

fun Connection.outboundLabel(): String = formatBound(outbound, outboundType)

/** Matched outbound is the last chain hop, else the direct outbound tag. */
fun Connection.matchedOutbound(): String =
    chainListList.lastOrNull()?.takeIf { it.isNotEmpty() } ?: outbound

/** Rule text; unmatched falls back to "final" (D-P1.8). */
fun Connection.matchedRuleOrFinal(): String =
    rule.ifEmpty { "final" }

fun Connection.chainLabel(): String =
    chainListList.joinToString(" => ")

/**
 * Formats a proto unix-millis timestamp as local `yyyy-MM-dd HH:mm:ss`, matching
 * Go's `time.DateTime` used by the old TrackerInfo getters. Zero → empty string.
 */
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

fun proxyDisplayName(type: String): String = Libcore.proxyDisplayName(type)

fun ConnectionEvent.isNew(): Boolean =
    type == ConnectionEventType.CONNECTION_EVENT_NEW

fun ConnectionEvent.isUpdate(): Boolean =
    type == ConnectionEventType.CONNECTION_EVENT_UPDATE

fun ConnectionEvent.isClosed(): Boolean =
    type == ConnectionEventType.CONNECTION_EVENT_CLOSED

/** Process paths / package names used by the dashboard connection detail UI. */
fun Connection.processNames(): List<String>? {
    if (!hasProcessInfo()) return null
    val packages = processInfo.packageNamesList
    if (packages.isNotEmpty()) return packages
    return processInfo.processPath.emptyAsNull()?.let { listOf(it) }
}

/**
 * UID for package-based process info, otherwise process id (desktop path).
 * Matches the old TrackerInfo mapping.
 */
fun Connection.processUid(): Int {
    if (!hasProcessInfo()) return -1
    return if (processInfo.packageNamesList.isNotEmpty()) {
        processInfo.userId
    } else {
        processInfo.processId
    }
}
