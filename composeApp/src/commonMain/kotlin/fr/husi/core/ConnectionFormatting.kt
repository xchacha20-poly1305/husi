package fr.husi.core

import fr.husi.ktx.emptyAsNull
import fr.husi.libcore.Libcore
import fr.husi.proto.daemon.Connection
import fr.husi.proto.daemon.ConnectionEvent
import fr.husi.proto.daemon.ConnectionEventType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    return CONNECTION_TIME_FORMAT.format(
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()),
    )
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

private val CONNECTION_TIME_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
