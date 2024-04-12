package io.nekohasekai.sagernet.fmt.http

import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.fmt.v2ray.setTLS
import libcore.Libcore

fun parseHttp(rawUrl: String): HttpBean {
    val url = Libcore.parseURL(rawUrl)

    if (url.rawPath != "/") error("Not http proxy")

    return HttpBean().apply {
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: if (url.scheme == "https") 443 else 80
        username = url.username
        password = url.password
        sni = url.queryParameterNotBlank("sni")
        name = url.fragment
        setTLS(url.scheme == "https")
    }
}

fun HttpBean.toUri(): String {
    val url = Libcore.newURL(if (isTLS()) "https" else "http").apply {
        host = serverAddress
    }

    if (serverPort in 1..65535) {
        url.ports = serverPort.toString()
    }

    if (username.isNotBlank()) {
        url.username = username
    }
    if (password.isNotBlank()) {
        url.password = password
    }
    if (sni.isNotBlank()) {
        url.addQueryParameter("sni", sni)
    }
    if (name.isNotBlank()) {
        url.setRawFragment(name)
    }

    return url.string
}