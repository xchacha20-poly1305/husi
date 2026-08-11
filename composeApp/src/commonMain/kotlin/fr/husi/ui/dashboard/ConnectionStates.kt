package fr.husi.ui.dashboard

import androidx.compose.runtime.Stable
import fr.husi.core.chainLabel
import fr.husi.core.formatConnectionTime
import fr.husi.core.inboundLabel
import fr.husi.core.matchedRuleOrFinal
import fr.husi.core.outboundLabel
import fr.husi.core.processNames
import fr.husi.core.processUid
import fr.husi.ktx.emptyAsNull
import fr.husi.proto.daemon.Connection

@Stable
data class ConnectionDetailState(
    val uuid: String = "",
    val inbound: String = "",
    val ipVersion: Short? = null,
    val network: String = "",
    val uploadTotal: Long = 0L,
    val downloadTotal: Long = 0L,
    val startedAt: String = "",
    val closedAt: String = "",
    val src: String = "",
    val dst: String = "",
    val host: String = "",
    val matchedRule: String = "",
    val outbound: String = "",
    val chain: String = "",
    val protocol: String? = null,
    val processes: List<String>? = null,
    val uid: Int = -1,
) {
    val isClosed: Boolean
        get() = closedAt.isNotEmpty()
}

fun Connection.toDetailState(): ConnectionDetailState {
    return ConnectionDetailState(
        uuid = id,
        inbound = inboundLabel(),
        ipVersion = ipVersion.takeIf { it > 0 }?.toShort(),
        network = network,
        uploadTotal = uplinkTotal,
        downloadTotal = downlinkTotal,
        startedAt = formatConnectionTime(createdAt),
        closedAt = formatConnectionTime(closedAt),
        src = source,
        dst = destination,
        host = domain,
        matchedRule = matchedRuleOrFinal(),
        outbound = outboundLabel(),
        chain = chainLabel(),
        protocol = protocol.emptyAsNull(),
        processes = processNames(),
        uid = processUid(),
    )
}
