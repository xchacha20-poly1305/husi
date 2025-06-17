package io.nekohasekai.sagernet.aidl

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class URLTestResult(
    val data: Map<String, Short> = emptyMap(),
) : Parcelable {
    constructor(results: libcore.ResultPairIterator) : this(
        LinkedHashMap<String, Short>(results.length()).apply {
            while (results.hasNext()) {
                val pair = results.next()
                this[pair.tag] = pair.delay
            }
        }
    )
}