package io.nekohasekai.sagernet.fmt.http

import io.nekohasekai.sagernet.fmt.parseBoxOutbound
import io.nekohasekai.sagernet.fmt.parseBoxTLS
import io.nekohasekai.sagernet.fmt.parseHeader
import io.nekohasekai.sagernet.fmt.v2ray.isTLS
import io.nekohasekai.sagernet.fmt.v2ray.setTLS
import io.nekohasekai.sagernet.ktx.JSONMap
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.toJSONMap
import libcore.Libcore

fun parseHttp(link: String): HttpBean = HttpBean().apply {
    val url = Libcore.parseURL(link)

    serverAddress = url.host
    serverPort = url.ports.toIntOrNull() ?: if (url.scheme == "https") 443 else 80
    username = url.username
    password = url.password
    sni = url.queryParameter("sni")
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

fun parseHttpOutbound(json: JSONMap): HttpBean = HttpBean().apply {
    parseBoxOutbound(json) { key, value ->
        when (key) {
            "username" -> username = value.toString()
            "password" -> password = value.toString()
            "path" -> path = value.toString()
            "headers" -> (value as? Map<*, *>)?.let {
                headers = parseHeader(it).map { entry ->
                    entry.key + ":" + entry.value.joinToString(",")
                }.joinToString("\n")
            }

            "tls" -> {
                val tlsJson = (value as? Map<*, *>)?.let {
                    toJSONMap(it)
                } ?: return@parseBoxOutbound
                val tls = parseBoxTLS(tlsJson)
                if (!tls.enabled) return@parseBoxOutbound

                setTLS(true)
                sni = tls.server_name
                alpn = tls.alpn?.joinToString(",")
                utlsFingerprint = tls.utls?.fingerprint
                allowInsecure = tls.insecure
                disableSNI = tls.disable_sni
                certificates = tls.certificate?.joinToString("\n")
                certPublicKeySha256 = tls.certificate_public_key_sha256?.joinToString("\n")
                clientCert = tls.client_certificate?.joinToString("\n")
                clientKey = tls.client_key?.joinToString("\n")
                tls.reality?.let {
                    realityPublicKey = it.public_key
                    realityShortID = it.short_id
                }
                tls.ech?.let {
                    ech = it.enabled
                    echConfig = it.config?.joinToString("\n")
                }
            }
        }
    }
}