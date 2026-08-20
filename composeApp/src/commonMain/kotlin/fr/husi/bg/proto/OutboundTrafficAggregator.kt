package fr.husi.bg.proto

import fr.husi.core.isClosed
import fr.husi.core.isNew
import fr.husi.core.isUpdate
import fr.husi.core.matchedOutbound
import fr.husi.fmt.TAG_DIRECT
import fr.husi.fmt.TrafficNode
import fr.husi.proto.daemon.Connection
import fr.husi.proto.daemon.ConnectionEvent
import fr.husi.proto.daemon.ConnectionEvents
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndUpdate

/** Bytes accumulated since the previous drain. */
data class TrafficDelta(val upload: Long = 0L, val download: Long = 0L)

/** Everything one drain took out of the aggregator. */
class TrafficSnapshot(
    /** Per profile id, the bytes its connections carried. */
    val byProfile: Map<Long, TrafficDelta>,
    /** Proxied bytes, counted once per connection however long its chain is. */
    val proxied: TrafficDelta,
    /** Bytes that bypassed the proxy. */
    val bypassed: TrafficDelta,
)

/**
 * Attributes connection traffic to the profiles that carried it.
 *
 * A connection names the outbound its rules matched plus the selectors sing-box
 * resolved from there — nested selectors included, since it keeps walking while each
 * hop is a group. It stops at the first hop that dials, so the rest of the chain
 * comes from [graph] instead. Selectors sitting behind a detour are invisible to that
 * walk as well; for those the last selection the groups stream reported is used.
 */
@OptIn(ExperimentalAtomicApi::class)
class OutboundTrafficAggregator(private val graph: Map<String, TrafficNode> = emptyMap()) {

    private class Counter {
        val upload = AtomicLong(0L)
        val download = AtomicLong(0L)

        fun add(upload: Long, download: Long) {
            this.upload.addAndFetch(upload)
            this.download.addAndFetch(download)
        }

        fun drain() = TrafficDelta(
            upload = upload.fetchAndUpdate { 0L },
            download = download.fetchAndUpdate { 0L },
        )
    }

    /** What one connection's bytes count towards, resolved once when it appears. */
    private class Attribution(val profileIDs: Set<Long>, val bypassed: Boolean)

    private val profileCounters = ConcurrentHashMap<Long, Counter>()
    private val proxiedCounter = Counter()
    private val bypassedCounter = Counter()
    private val selectedByGroup = ConcurrentHashMap<String, String>()
    private val attributionById = ConcurrentHashMap<String, Attribution>()

    /** Last seen cumulative up/down per connection id; survives stream resets. */
    private val idTotals = ConcurrentHashMap<String, Pair<Long, Long>>()

    /** Resolves selectors that no connection can report because a detour hides them. */
    fun updateSelection(groupTag: String, selected: String) {
        selectedByGroup[groupTag] = selected
    }

    fun onEvents(events: ConnectionEvents) {
        if (events.reset) {
            val liveIds = events.eventsList.mapTo(HashSet()) { it.id }
            attributionById.clear()
            // Drop watermarks for connections absent from the replay batch.
            idTotals.keys.retainAll(liveIds)
            // Do not clear the counters — those may already have been drained into
            // profile totals and persisted.
        }
        for (event in events.eventsList) {
            onEvent(event)
        }
    }

    fun onEvent(event: ConnectionEvent) {
        when {
            event.isNew() -> {
                val connection = event.connection ?: return
                val attribution = connection.attribution() ?: return
                attributionById[event.id] = attribution
                val watermark = idTotals[event.id] ?: (0L to 0L)
                val upload = (connection.uplinkTotal - watermark.first).coerceAtLeast(0L)
                val download = (connection.downlinkTotal - watermark.second).coerceAtLeast(0L)
                idTotals[event.id] = connection.uplinkTotal to connection.downlinkTotal
                if (upload == 0L && download == 0L) return
                credit(attribution, upload, download)
            }

            event.isUpdate() -> {
                attributionById[event.id]?.let {
                    credit(it, event.uplinkDelta, event.downlinkDelta)
                }
                idTotals.compute(event.id) { _, previous ->
                    val (upload, download) = previous ?: (0L to 0L)
                    (upload + event.uplinkDelta) to (download + event.downlinkDelta)
                }
            }

            event.isClosed() -> {
                attributionById.remove(event.id)
                idTotals.remove(event.id)
            }
        }
    }

    fun drain(): TrafficSnapshot = TrafficSnapshot(
        byProfile = profileCounters.mapValues { it.value.drain() },
        proxied = proxiedCounter.drain(),
        bypassed = bypassedCounter.drain(),
    )

    private fun credit(attribution: Attribution, upload: Long, download: Long) {
        if (attribution.bypassed) {
            bypassedCounter.add(upload, download)
            return
        }
        proxiedCounter.add(upload, download)
        for (id in attribution.profileIDs) {
            profileCounters.computeIfAbsent(id) { Counter() }.add(upload, download)
        }
    }

    private fun Connection.attribution(): Attribution? {
        val matched = matchedOutbound().ifEmpty { return null }
        if (matched !in graph) {
            return if (matched == TAG_DIRECT) {
                Attribution(emptySet(), bypassed = true)
            } else {
                null // Rejected, hijacked or otherwise not a profile of ours.
            }
        }
        return Attribution(carriers(matched), bypassed = false)
    }

    /** Walks from the matched outbound down to the hop that dials. */
    private fun Connection.carriers(matched: String): Set<Long> {
        // chainList runs from the dialing hop up to the matched outbound, so reading
        // it backwards says which member each selector on the way resolved to.
        val resolvedByChain = HashMap<String, String>(chainListList.size)
        for (index in 1..chainListList.lastIndex) {
            resolvedByChain[chainListList[index]] = chainListList[index - 1]
        }

        val carriers = LinkedHashSet<Long>()
        val visited = HashSet<String>()
        var tag: String? = matched
        while (tag != null && visited.add(tag)) {
            val node = graph[tag] ?: break
            carriers += node.profileIDs
            tag = if (node.memberTags.isEmpty()) {
                node.detour
            } else {
                val selected = resolvedByChain[tag] ?: selectedByGroup[tag]
                selected?.takeIf { it in node.memberTags }
            }
        }
        return carriers
    }
}
