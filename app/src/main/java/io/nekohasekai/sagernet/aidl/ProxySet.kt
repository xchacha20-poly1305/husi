package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import libcore.GroupItemIterator

@Parcelize
data class ProxySet(
    val tag: String = "",
    val type: String = "",
    val selectable: Boolean = false,
    var selected: String = "",
    val items: List<ProxySetItem> = emptyList(),
) : Parcelable {
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

@Parcelize
data class ProxySetItem(
    val tag: String = "",
    val type: String = "",
    var urlTestDelay: Short = -1,
) : Parcelable {
    constructor(item: libcore.GroupItem) : this(
        tag = item.tag,
        type = item.type,
    )
}

fun GroupItemIterator.toList(): List<ProxySetItem> {
    return ArrayList<ProxySetItem>(length()).apply {
        while (hasNext()) {
            add(ProxySetItem(next()))
        }
    }
}
