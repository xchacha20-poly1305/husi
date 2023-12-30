package io.nekohasekai.sagernet.ktx

import libcore.StringIterator

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