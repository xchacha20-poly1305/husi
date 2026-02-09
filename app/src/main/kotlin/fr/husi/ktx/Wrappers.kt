package fr.husi.ktx

import fr.husi.libcore.StringIterator
import fr.husi.libcore.TrackerInfo
import fr.husi.libcore.TrackerInfoIterator

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
