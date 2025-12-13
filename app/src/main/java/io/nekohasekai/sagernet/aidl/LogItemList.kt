package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import kotlinx.parcelize.Parcelize
import libcore.LogItemIterator

@Immutable
@Parcelize
data class LogItemList(
    val list: List<LogItem>,
) : Parcelable

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
@Parcelize
data class LogItem(
    val level: Short,
    val message: String,
) : Parcelable {
    constructor(item: libcore.LogItem) : this(
        level = item.level,
        message = item.message,
    )
}