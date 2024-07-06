@file:Suppress("SpellCheckingInspection")

package io.nekohasekai.sagernet.ktx

import io.nekohasekai.sagernet.BuildConfig
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.AbstractBean
import libcore.Libcore
import libcore.SocksInfo
import libcore.URL
import moe.matsuri.nb4a.utils.NGUtil
import java.net.InetSocketAddress
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

fun String.isIpAddress(): Boolean {
    return NGUtil.isIpv4Address(this) || NGUtil.isIpv6Address(this)
}

fun String.isIpAddressV6(): Boolean {
    return NGUtil.isIpv6Address(this)
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
    if (unwrapped.isIpAddressV6()) {
        return "[$unwrapped]"
    } else {
        return this
    }
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

val USER_AGENT by lazy { "husi/${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}; sing-box ${Libcore.versionBox()})" }

fun socksInfo(): SocksInfo {
    return SocksInfo(
        DataStore.mixedPort.toString(),
        DataStore.inboundUsername,
        DataStore.inboundPassword,
    )
}