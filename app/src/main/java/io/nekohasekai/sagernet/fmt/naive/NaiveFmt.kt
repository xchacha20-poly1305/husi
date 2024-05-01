package io.nekohasekai.sagernet.fmt.naive

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST4
import io.nekohasekai.sagernet.ktx.*
import libcore.Libcore
import libcore.URL
import org.json.JSONObject

fun parseNaive(url: URL): NaiveBean {
    return NaiveBean().also {
        it.proto = url.scheme.substringAfter("+").substringBefore(":")
    }.apply {
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: 443
        username = url.username
        try {
            password = url.password
        } catch (_: Exception) {
        }
        sni = url.queryParameterNotBlank("sni")
        extraHeaders =
            url.queryParameterNotBlank("extra-headers")?.unUrlSafe()?.replace("\r\n", "\n")
        insecureConcurrency = url.queryParameterNotBlank("insecure-concurrency")?.toIntOrNull()
        name = url.fragment
        initializeDefaultValues()
    }
}

fun NaiveBean.toUri(proxyOnly: Boolean = false): String {
    val builder = Libcore.newURL(if (proxyOnly) proto else "naive+$proto").apply {
        host = finalAddress
        ports = finalPort.toString()
    }
    if (username.isNotBlank()) {
        builder.username = username
    }
    if (password.isNotBlank()) {
        builder.password = password
    }
    if (!proxyOnly) {
        if (sni.isNotBlank()) {
            builder.addQueryParameter("sni", sni)
        }
        if (extraHeaders.isNotBlank()) {
            builder.addQueryParameter("extra-headers", extraHeaders)
        }
        if (name.isNotBlank()) {
            builder.setRawFragment(name)
        }
        if (insecureConcurrency > 0) {
            builder.addQueryParameter("insecure-concurrency", "$insecureConcurrency")
        }
    }
    return builder.string
}

fun NaiveBean.buildNaiveConfig(port: Int): String {
    return JSONObject().apply {
        // process ipv6
        finalAddress = finalAddress.wrapIPV6Host()
        serverAddress = serverAddress.wrapIPV6Host()

        // process sni
        if (sni.isNotBlank()) {
            put("host-resolver-rules", "MAP $sni $finalAddress")
            finalAddress = sni
        } else {
            if (serverAddress.isIpAddress()) {
                // for naive, using IP as SNI name hardly happens
                // and host-resolver-rules cannot resolve the SNI problem
                // so do nothing
            } else {
                put("host-resolver-rules", "MAP $serverAddress $finalAddress")
                finalAddress = serverAddress
            }
        }

        put("listen", "socks://$LOCALHOST4:$port")
        put("proxy", toUri(true))
        if (extraHeaders.isNotBlank()) {
            put("extra-headers", extraHeaders.split("\n").joinToString("\r\n"))
        }
        if (DataStore.logLevel > 0) {
            put("log", "")
        }
        if (insecureConcurrency > 0) {
            put("insecure-concurrency", insecureConcurrency)
        }
    }.toStringPretty()
}