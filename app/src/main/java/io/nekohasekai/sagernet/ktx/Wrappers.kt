package io.nekohasekai.sagernet.ktx

import io.nekohasekai.sagernet.aidl.Connection
import libcore.StringIterator
import libcore.TrackerInfo
import libcore.TrackerInfoIterator

fun Iterable<String>.toStringIterator(size: Int): StringIterator {
    return object : StringIterator {
        val iterator = iterator()

        override fun hasNext(): Boolean {
            return iterator.hasNext()
        }

        override fun next(): String {
            return iterator.next()
        }

        override fun length(): Int = size
    }
}

fun StringIterator.toList(): List<String> = ArrayList<String>(length()).apply {
    while (hasNext()) {
        add(next())
    }
}

fun TrackerInfoIterator.toList(): List<TrackerInfo> = ArrayList<TrackerInfo>(length()).apply {
    while (hasNext()) {
        add(next())
    }
}

fun TrackerInfoIterator.toConnectionList(): List<Connection> {
    return ArrayList<Connection>(length()).apply {
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
                    protocol = trackerInfo.protocol.blankAsNull(),
                    process = trackerInfo.process.blankAsNull(),
                    closed = trackerInfo.closed,
                )
            )
        }
    }
}