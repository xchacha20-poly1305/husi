package fr.husi.bg.proto

import fr.husi.ktx.emptyAsNull
import fr.husi.libcore.ConnectionEvent
import fr.husi.libcore.Libcore
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

    fun onEvent(event: ConnectionEvent) {
        when (event.type) {
            Libcore.ConnectionEventNew -> {
                val info = event.trackerInfo ?: return
                val tag = info.matchedOutbound.emptyAsNull() ?: return
                idToTag[event.id] = tag

                tagToCounter.computeIfAbsent(tag) { OutboundCounter() }.apply {
                    upload.addAndFetch(info.uploadTotal)
                    download.addAndFetch(info.downloadTotal)
                }
            }

            Libcore.ConnectionEventUpdate -> {
                val tag = idToTag[event.id] ?: return
                tagToCounter[tag]?.let { counter ->
                    counter.upload.addAndFetch(event.uplinkDelta)
                    counter.download.addAndFetch(event.downlinkDelta)
                }
            }

            Libcore.ConnectionEventClosed -> {
                idToTag.remove(event.id)
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
