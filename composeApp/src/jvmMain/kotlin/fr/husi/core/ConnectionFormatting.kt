package fr.husi.core

import fr.husi.ktx.emptyAsNull
import fr.husi.proto.daemon.Connection

/** "$name/$type" label composition previously done in Go generateBound. */
private fun formatBound(name: String, type: String): String {
    if (name.isEmpty()) return type
    if (type.isEmpty()) return name
    return "$name/$type"
}

fun Connection.inboundLabel(): String = formatBound(inbound, inboundType)

fun Connection.outboundLabel(): String = formatBound(outbound, outboundType)

/**
 * Hops in traffic order, matched outbound first, the hop that dials last.
 * `chainList` runs from the dialing hop up to the matched outbound.
 */
fun Connection.chainHops(): List<String> = chainListList.asReversed()

/** Matched outbound is the first chain hop, else the direct outbound tag. */
fun Connection.matchedOutbound(): String =
    chainHops().firstOrNull()?.takeIf { it.isNotEmpty() } ?: outbound

/** Rule text; unmatched falls back to "final" (D-P1.8). */
fun Connection.matchedRuleOrFinal(): String =
    rule.ifEmpty { "final" }

fun Connection.chainLabel(): String =
    chainHops().joinToString(" => ")

/**
 * Formats a proto unix-millis timestamp as local `yyyy-MM-dd HH:mm:ss`, matching
 * Go's `time.DateTime` used by the old TrackerInfo getters. Zero → empty string.
 */


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
