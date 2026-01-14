package io.nekohasekai.sagernet.aidl

import androidx.compose.runtime.Immutable
import libcore.GroupItemIterator

@Immutable
data class ProxySet(
    val tag: String = "",
    val type: String = "",
    val selectable: Boolean = false,
    var selected: String = "",
    var items: List<ProxySetItem> = emptyList(),
    val isTesting: Boolean = false,
) {
    constructor(set: libcore.ProxySet) : this(
        tag = set.tag,
        type = set.type,
        selectable = set.selectable,
        selected = set.selected,
        items = set.items.toList(),
    )
}

fun libcore.ProxySetIterator.toList(): List<ProxySet> {
    return ArrayList<ProxySet>(length()).apply {
        while (hasNext()) {
            add(ProxySet(next()))
        }
    }
}

@Immutable
data class ProxySetItem(
    val tag: String = "",
    val type: String = "",
    val urlTestDelay: Short = -1,
) {
    constructor(item: libcore.GroupItem) : this(
        tag = item.tag,
        type = item.type,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ProxySetItem

        if (tag != other.tag) return false
        if (type != other.type) return false

        return true
    }

    override fun hashCode(): Int {
        var result = tag.hashCode()
        result = 31 * result + type.hashCode()
        return result
    }
}

fun GroupItemIterator.toList(): List<ProxySetItem> {
    return ArrayList<ProxySetItem>(length()).apply {
        while (hasNext()) {
            add(ProxySetItem(next()))
        }
    }
}
