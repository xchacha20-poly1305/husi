package io.nekohasekai.sagernet.aidl

import androidx.compose.runtime.Immutable
import libcore.LogItemIterator

@Immutable
data class LogItemList(
    val list: List<LogItem>,
)

fun LogItemIterator.toList(): LogItemList {
    return LogItemList(
        list = ArrayList<LogItem>(length()).apply {
            while (hasNext()) {
                add(LogItem(next()))
            }
        },
    )
}

@Immutable
data class LogItem(
    val level: Short,
    val message: String,
) {
    constructor(item: libcore.LogItem) : this(
        level = item.level,
        message = item.message,
    )
}
