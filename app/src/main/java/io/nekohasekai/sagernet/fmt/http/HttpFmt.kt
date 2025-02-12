package io.nekohasekai.sagernet.fmt.http

import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.fmt.v2ray.setTLS
import io.nekohasekai.sagernet.ktx.blankAsNull
import libcore.Libcore

fun parseHttp(link: String): HttpBean = HttpBean().apply {
    val url = Libcore.parseURL(link)

    serverAddress = url.host
    serverPort = url.ports.toIntOrNull() ?: if (url.scheme == "https") 443 else 80
    username = url.username
    try {
        password = url.password
    } catch (_: Exception) {
    }
    sni = url.queryParameterNotBlank("sni")
    name = url.fragment
    setTLS(url.scheme == "https")
    path = url.path
}

fun HttpBean.toUri(): String {
    val url = Libcore.newURL(if (isTLS()) "https" else "http").apply {
        host = serverAddress
    }

    if (serverPort in 1..65535) {
        url.ports = serverPort.toString()
    }

    username?.blankAsNull()?.let { url.username = it }
    password?.blankAsNull()?.let { url.password = it }
    path?.blankAsNull()?.let { url.rawPath = it }
    sni?.blankAsNull()?.let { url.addQueryParameter("sni", it) }
    name?.blankAsNull()?.let { url.fragment = it }


    return url.string
}
