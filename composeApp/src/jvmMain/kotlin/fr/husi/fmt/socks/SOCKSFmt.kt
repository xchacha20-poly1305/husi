package fr.husi.fmt.socks

import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.parseBoxOutbound
import fr.husi.fmt.parseBoxUot
import fr.husi.ktx.JSONMap
import fr.husi.ktx.b64DecodeToString
import fr.husi.ktx.blankAsNull
import io.github.xchacha20_poly1305.kpuri.Url
import io.github.xchacha20_poly1305.kpuri.buildUrl

fun parseSOCKS(link: String): SOCKSBean {
    val url = Url.parse(link)
    return SOCKSBean().apply {
        protocol = when (url.scheme) {
            "socks4" -> SOCKSBean.PROTOCOL_SOCKS4
            "socks4a" -> SOCKSBean.PROTOCOL_SOCKS4A
            else -> SOCKSBean.PROTOCOL_SOCKS5
        }
        name = url.fragment.orEmpty()
        serverAddress = url.host.orEmpty()
        serverPort = url.port?.toIntOrNull() ?: 1080
        username = url.username.orEmpty()
        password = url.password.orEmpty()
        // v2rayN fmt
        if (password.isBlank() && username.isNotBlank()) {
            try {
                val n = username.b64DecodeToString()
                username = n.substringBefore(":")
                password = n.substringAfter(":")
            } catch (_: Exception) {
            }
        }
    }
}

fun SOCKSBean.toUri(): String = buildUrl("socks${protocolVersion()}") {
    host = serverAddress
    port = serverPort.toString()
    this@toUri.username.blankAsNull()?.let { username = it }
    this@toUri.password.blankAsNull()?.let { password = it }
    name.blankAsNull()?.let { fragment = it }
}.toString()

fun buildSingBoxOutboundSocksBean(bean: SOCKSBean): SingBoxOptions.Outbound_SOCKSOptions {
    return SingBoxOptions.Outbound_SOCKSOptions().apply {
        type = SingBoxOptions.TYPE_SOCKS
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        version = bean.protocolVersionName()
    }
}

fun parseSocksOutbound(json: JSONMap): SOCKSBean = SOCKSBean().apply {
    parseBoxOutbound(json) { key, value ->
        when (key) {
            "username" -> username = value.toString()
            "password" -> password = value.toString()
            "version" -> protocol = when (value.toString()) {
                "4" -> SOCKSBean.PROTOCOL_SOCKS4
                "4a" -> SOCKSBean.PROTOCOL_SOCKS4A
                else -> SOCKSBean.PROTOCOL_SOCKS5
            }

            "udp_over_tcp" -> udpOverTcp = parseBoxUot(value)
        }
    }
}