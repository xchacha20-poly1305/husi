package io.nekohasekai.sagernet.ktx

import io.nekohasekai.sagernet.aidl.Connection
import libcore.StringIterator
import libcore.TrackerInfo
import libcore.TrackerInfoIterator

fun Iterable<String>.toStringIterator(): StringIterator {
    return object : StringIterator {
        val iterator = iterator()

        override fun hasNext(): Boolean {
            return iterator.hasNext()
        }

        override fun next(): String {
            return iterator.next()
        }
    }
}

fun StringIterator.toList(): List<String> {
    return mutableListOf<String>().apply {
        while (hasNext()) {
            add(next())
        }
    }
}

fun TrackerInfoIterator.toList(): List<TrackerInfo> {
    return mutableListOf<TrackerInfo>().apply {
        while (hasNext()) {
            add(next())
        }
    }
}

fun TrackerInfoIterator.toConnectionList(): List<Connection> {
    return mutableListOf<Connection>().apply {
        while (hasNext()) {
            val trackerInfo = next()
            add(
                Connection(
                    uuid = trackerInfo.uuid,
                    inbound = trackerInfo.inbound,
                    ipVersion = trackerInfo.ipVersion.let { if (it > 0) it else null },
                    network = trackerInfo.network,
                    uploadTotal = trackerInfo.uploadTotal,
                    downloadTotal = trackerInfo.downloadTotal,
                    start = trackerInfo.start,
                    src = trackerInfo.src,
                    dst = trackerInfo.dst,
                    host = trackerInfo.host,
                    matchedRule = trackerInfo.matchedRule,
                    outbound = trackerInfo.outbound,
                    chain = trackerInfo.chain,
                    protocol = trackerInfo.protocol.let { if (it.isBlank()) null else it },
                )
            )
        }
    }
}