package fr.husi.ktx

import fr.husi.DOMAIN_STRATEGY_AUTO
import fr.husi.database.DataStore
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.LOCALHOST4
import fr.husi.fmt.LOCALHOST_NAME
import fr.husi.fmt.SingBoxOptions
import io.github.xchacha20_poly1305.kpuri.Url
import io.github.xchacha20_poly1305.kpuri.buildUrl
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.Socket

fun Url.queryParameterNotBlank(key: String): String? {
    return queryParameter(key).blankAsNull()
}

fun Url.parseBoolean(key: String): Boolean = when (queryParameter(key)?.lowercase()) {
    "1", "true", "yes" -> true
    else -> false
}

suspend fun localProxyURL(scheme: String): Url {
    val mixedPort = DataStore.mixedPort.get()
    val inboundUsername = DataStore.inboundUsername.get().emptyAsNull()
    val inboundPassword = DataStore.inboundPassword.get()
    return buildUrl(scheme) {
        host = LOCALHOST4
        port = mixedPort.toString()

        inboundUsername?.let { name ->
            username = name
            password = inboundPassword
        }
    }
}

suspend fun currentSocks5(): Url? = if (!DataStore.serviceState.connected) {
    null
} else {
    localProxyURL("socks5")
}

fun String.isIpAddress(): Boolean {
    return isIPv4() || isIPv6()
}

suspend fun serverAddressDomainStrategy(): String? {
    val domainStrategy = DataStore.domainStrategyForServer.get()
        .replace(DOMAIN_STRATEGY_AUTO, "")
        .blankAsNull()
    val networkStrategy = DataStore.networkStrategy.get().blankAsNull()
    return defaultOr(
        domainStrategy,
        { networkStrategy },
    )
}

fun List<InetAddress>.selectByNetworkStrategy(networkStrategy: String): InetAddress? {
    val candidates = when (networkStrategy) {
        SingBoxOptions.STRATEGY_IPV4_ONLY -> filterIsInstance<Inet4Address>()
        SingBoxOptions.STRATEGY_IPV6_ONLY -> filterIsInstance<Inet6Address>()
        else -> this
    }

    return when (networkStrategy) {
        SingBoxOptions.STRATEGY_PREFER_IPV4 -> {
            candidates.firstOrNull { it is Inet4Address } ?: candidates.firstOrNull()
        }

        SingBoxOptions.STRATEGY_PREFER_IPV6 -> {
            candidates.firstOrNull { it is Inet6Address } ?: candidates.firstOrNull()
        }

        else -> candidates.firstOrNull()
    }
}

fun String.isIPv4(): Boolean {
    return Regex("^([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])$")
        .matches(this)
}

fun String.isIPv6(): Boolean {
    var addr = this
    if (addr.indexOf("[") == 0 && addr.lastIndexOf("]") > 0) {
        addr = addr.drop(1)
        addr = addr.dropLast(addr.count() - addr.lastIndexOf("]"))
    }
    val regV6 =
        Regex("^((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$")
    return regV6.matches(addr)
}

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
    return if (unwrapped.isIPv6()) {
        "[$unwrapped]"
    } else {
        this
    }
}

fun joinAddress(host: String, port: Int): String = joinAddress(host, port.toString())

fun joinAddress(host: String, port: String): String = "${host.wrapIPV6Host()}:$port"

fun splitAddress(address: String): Pair<String, String>? {
    val separator = address.lastIndexOf(':')
    if (separator <= 0 || separator == address.lastIndex) return null
    val host = address.substring(0, separator)
    val bracketed = host.startsWith("[") && host.endsWith("]")
    if (!bracketed && ':' in host) return null
    return host.unwrapIPV6Host() to address.substring(separator + 1)
}

private val VALID_PORTS = 1..65535

fun String.toPortOrNull(): Int? = toIntOrNull()?.takeIf { it in VALID_PORTS }

private const val ADDRESS_MASK = "***"
private const val MASKED_IPV4_TAIL = ".*.*.*"

fun String.blurAddress(): String {
    val (host, port) = splitAddress(this) ?: return blurHost()
    val blurredHost = host.blurHost()
    val displayHost = if (host.isIPv6()) "[$blurredHost]" else blurredHost
    return "$displayHost:${port.blurLabel()}"
}

private fun String.blurHost(): String = when {
    isBlank() -> this

    startsWith("[") && endsWith("]") -> "[${unwrapIPV6Host().blurHost()}]"

    isIPv4() -> substringBefore('.') + MASKED_IPV4_TAIL

    isIPv6() -> "${substringBefore(':')}:$ADDRESS_MASK"

    else -> blurDomain()
}

private fun String.blurDomain(): String {
    val labels = split('.')
    val topLevelIndex = if (labels.size > 1) labels.lastIndex else -1
    return labels.mapIndexed { index, label ->
        if (index == topLevelIndex) label else label.blurLabel()
    }.joinToString(".")
}

private fun String.blurLabel(): String {
    if (isEmpty()) return this
    return "${first()}$ADDRESS_MASK"
}

fun String.isLoopbackHost(): Boolean {
    if (equals(LOCALHOST_NAME, ignoreCase = true)) return true
    val literal = unwrapIPV6Host()
    if (!literal.isIpAddress()) return false
    return runCatching { InetAddress.getByName(literal).isLoopbackAddress }.getOrDefault(false)
}

fun AbstractBean.wrapUri(): String {
    return joinAddress(finalAddress, finalPort)
}

fun mkPort(): Int {
    val socket = Socket()
    socket.reuseAddress = true
    socket.bind(InetSocketAddress(0))
    val port = socket.localPort
    socket.close()
    return port
}

fun InterfaceAddress.toPrefix(): String {
    return if (address is Inet6Address) {
        "${Inet6Address.getByAddress(address.address).hostAddress}/${networkPrefixLength}"
    } else {
        "${address.hostAddress}/${networkPrefixLength}"
    }
}
