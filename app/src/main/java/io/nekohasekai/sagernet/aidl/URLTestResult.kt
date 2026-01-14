package io.nekohasekai.sagernet.aidl

data class URLTestResult(
    val data: Map<String, Short> = emptyMap(),
) {
    constructor(results: libcore.ResultPairIterator) : this(
        LinkedHashMap<String, Short>(results.length()).apply {
            while (results.hasNext()) {
                val pair = results.next()
                this[pair.tag] = pair.delay
            }
        }
    )
}
