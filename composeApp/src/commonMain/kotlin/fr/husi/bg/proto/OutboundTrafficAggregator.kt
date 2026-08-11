package fr.husi.bg.proto

import fr.husi.core.isClosed
import fr.husi.core.isNew
import fr.husi.core.isUpdate
import fr.husi.core.matchedOutbound
import fr.husi.proto.daemon.ConnectionEvent
import fr.husi.proto.daemon.ConnectionEvents
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.fetchAndUpdate

@OptIn(ExperimentalAtomicApi::class)
class OutboundTrafficAggregator {

    private class OutboundCounter(
        val upload: AtomicLong = AtomicLong(0L),
        val download: AtomicLong = AtomicLong(0L),
    )

    private val idToTag = ConcurrentHashMap<String, String>()
    private val tagToCounter = ConcurrentHashMap<String, OutboundCounter>()
    /** Last seen cumulative up/down per connection id; survives stream resets. */
    private val idTotals = ConcurrentHashMap<String, Pair<Long, Long>>()

    fun onEvents(events: ConnectionEvents) {
        if (events.reset) {
            val liveIds = events.eventsList.mapTo(HashSet()) { it.id }
            idToTag.clear()
            // Drop watermarks for connections absent from the replay batch.
            idTotals.keys.retainAll(liveIds)
            // Do not clear tag counters — those may already have been drained
            // into TrafficLooperData and persisted.
        }
        for (event in events.eventsList) {
            onEvent(event)
        }
    }

    fun onEvent(event: ConnectionEvent) {
        when {
            event.isNew() -> {
                val connection = event.connection ?: return
                val tag = connection.matchedOutbound().ifEmpty { return }
                idToTag[event.id] = tag
                val watermark = idTotals[event.id] ?: (0L to 0L)
                val upCredit = (connection.uplinkTotal - watermark.first).coerceAtLeast(0L)
                val downCredit = (connection.downlinkTotal - watermark.second).coerceAtLeast(0L)
                idTotals[event.id] = connection.uplinkTotal to connection.downlinkTotal
                if (upCredit == 0L && downCredit == 0L) return
                tagToCounter.computeIfAbsent(tag) { OutboundCounter() }.apply {
                    upload.addAndFetch(upCredit)
                    download.addAndFetch(downCredit)
                }
            }

            event.isUpdate() -> {
                val tag = idToTag[event.id] ?: return
                tagToCounter.computeIfAbsent(tag) { OutboundCounter() }.let { counter ->
                    counter.upload.addAndFetch(event.uplinkDelta)
                    counter.download.addAndFetch(event.downlinkDelta)
                }
                idTotals.compute(event.id) { _, prev ->
                    val (up, down) = prev ?: (0L to 0L)
                    (up + event.uplinkDelta) to (down + event.downlinkDelta)
                }
            }

            event.isClosed() -> {
                idToTag.remove(event.id)
                idTotals.remove(event.id)
            }
        }
    }

    fun drain(tag: String, isUpload: Boolean): Long {
        val counter = tagToCounter[tag] ?: return 0L
        return if (isUpload) {
            counter.upload.fetchAndUpdate { 0L }
        } else {
            counter.download.fetchAndUpdate { 0L }
        }
    }
}
