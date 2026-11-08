package fr.husi.ui.dashboard

import fr.husi.core.chainLabel
import fr.husi.core.formatConnectionTime
import fr.husi.core.inboundLabel
import fr.husi.core.matchedRuleOrFinal
import fr.husi.core.outboundLabel
import fr.husi.core.processNames
import fr.husi.core.processUid
import fr.husi.ktx.emptyAsNull
import fr.husi.proto.daemon.Connection

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
