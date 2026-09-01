package fr.husi.ktx

import fr.husi.BuildConfig
import fr.husi.DOMAIN_STRATEGY_AUTO
import fr.husi.database.DataStore
import fr.husi.fmt.AbstractBean
import fr.husi.fmt.LOCALHOST4
import fr.husi.fmt.LOCALHOST_NAME
import fr.husi.fmt.SingBoxOptions
import fr.husi.libcore.Libcore
import fr.husi.libcore.URL
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.InterfaceAddress
import java.net.Socket

var URL.pathSegments: List<String>
    get() = path.split("/").filter { it.isNotBlank() }
    set(value) {
        path = value.joinToString("/")
    }

fun URL.addPathSegments(vararg segments: String) {
    pathSegments = pathSegments.toMutableList().apply {
        addAll(segments)
    }
}

fun URL.queryParameterNotBlank(key: String): String? {
    return queryParameter(key).blankAsNull()
}

fun URL.queryParameterUnescapeNotBlank(key: String): String? {
    return queryParameterUnescape(key).blankAsNull()
}

fun URL.parseBoolean(key: String): Boolean = when (queryParameter(key).lowercase()) {
    "1", "true", "yes" -> true
    else -> false
}

suspend fun localProxyURL(scheme: String): URL = Libcore.newURL(scheme).apply {
    host = LOCALHOST4
    ports = DataStore.mixedPort.get().toString()

    DataStore.inboundUsername.get().emptyAsNull()?.let { name ->
        username = name
        password = DataStore.inboundPassword.get()
    }
}

suspend fun currentSocks5(): URL? = if (!DataStore.serviceState.connected) {
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

private const val ADDRESS_MASK = "***"
private const val MASKED_IPV4_TAIL = ".*.*.*"

fun String.blurAddress(): String {
    val (host, port) = splitHostAndPort()
    val blurredHost = host.blurHost()
    val blurredPort = port?.blurLabel()
    return if (blurredPort == null) blurredHost else "$blurredHost:$blurredPort"
}

private fun String.splitHostAndPort(): Pair<String, String?> {
    if (startsWith("[")) {
        val closingBracket = indexOf(']')
        if (closingBracket < 0) return this to null
        val host = substring(0, closingBracket + 1)
        val port = substring(closingBracket + 1).removePrefix(":").blankAsNull()
        return host to port
    }

    val separator = indexOf(':')
    val isBareIPv6 = separator >= 0 && indexOf(':', separator + 1) >= 0
    if (separator < 0 || isBareIPv6) return this to null
    return substring(0, separator) to substring(separator + 1).blankAsNull()
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
    return "${finalAddress.wrapIPV6Host()}:$finalPort"
}

fun mkPort(): Int {
    val socket = Socket()
    socket.reuseAddress = true
    socket.bind(InetSocketAddress(0))
    val port = socket.localPort
    socket.close()
    return port
}

val USER_AGENT by lazy { "husi/${BuildConfig.VERSION_NAME} (sing-box ${Libcore.versionBox()})" }

/**
 * Replace all version-about escapes in User-Agent
 */
fun generateUserAgent(userAgent: String): String {
    if (userAgent.isBlank()) return USER_AGENT
    return userAgent.replace($$"$version", BuildConfig.VERSION_NAME)
        .replace($$"$box_version", Libcore.versionBox())
}

fun InterfaceAddress.toPrefix(): String {
    return if (address is Inet6Address) {
        "${Inet6Address.getByAddress(address.address).hostAddress}/${networkPrefixLength}"
    } else {
        "${address.hostAddress}/${networkPrefixLength}"
    }
}