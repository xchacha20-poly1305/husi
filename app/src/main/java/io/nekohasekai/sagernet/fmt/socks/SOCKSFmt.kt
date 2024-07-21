package io.nekohasekai.sagernet.fmt.socks

import io.nekohasekai.sagernet.ktx.decodeBase64UrlSafe
import libcore.Libcore
import moe.matsuri.nb4a.SingBoxOptions

fun parseSOCKS(link: String): SOCKSBean {
    val url = Libcore.parseURL(link)
    return SOCKSBean().apply {
        protocol = when (url.scheme) {
            "socks4" -> SOCKSBean.PROTOCOL_SOCKS4
            "socks4a" -> SOCKSBean.PROTOCOL_SOCKS4A
            else -> SOCKSBean.PROTOCOL_SOCKS5
        }
        name = url.fragment
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: 1080
        username = url.username
        try {
            password = url.password
        } catch (_: Exception) {
        }
        // v2rayN fmt
        if (password.isNullOrBlank() && !username.isNullOrBlank()) {
            try {
                val n = username.decodeBase64UrlSafe()
                username = n.substringBefore(":")
                password = n.substringAfter(":")
            } catch (_: Exception) {
            }
        }
    }
}

fun SOCKSBean.toUri(): String {

    val builder = Libcore.newURL("socks${protocolVersion()}").apply {
        host = serverAddress
        ports = serverPort.toString()
    }
    if (!username.isNullOrBlank()) builder.username = username
    if (!password.isNullOrBlank()) builder.password = password
    if (!name.isNullOrBlank()) builder.fragment = name
    return builder.string

}

fun buildSingBoxOutboundSocksBean(bean: SOCKSBean): SingBoxOptions.Outbound_SocksOptions {
    return SingBoxOptions.Outbound_SocksOptions().apply {
        type = "socks"
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        version = bean.protocolVersionName()
    }
}
