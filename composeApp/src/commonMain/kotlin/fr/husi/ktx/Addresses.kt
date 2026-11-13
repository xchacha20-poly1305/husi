package fr.husi.ktx

// [2001:4860:4860::8888] -> 2001:4860:4860::8888
fun String.unwrapIPV6Host(): String {
    if (startsWith("[") && endsWith("]")) {
        return substring(1, length - 1).unwrapIPV6Host()
    }
    return this
}

// [2001:4860:4860::8888] or 2001:4860:4860::8888 -> [2001:4860:4860::8888]
fun String.wrapIPV6Host(): String {
    val unwrapped = this.unwrapIPV6Host()
    return if (':' in unwrapped) {
        "[$unwrapped]"
    } else {
        this
    }
}

fun joinAddress(host: String, port: Int): String = joinAddress(host, port.toString())

fun joinAddress(host: String, port: String): String = if (port.isBlank()) {
    host
} else {
    "${host.wrapIPV6Host()}:$port"
}
