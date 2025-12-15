package io.nekohasekai.sagernet.fmt.naive

import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.fmt.LOCALHOST4
import io.nekohasekai.sagernet.fmt.SingBoxOptions
import io.nekohasekai.sagernet.fmt.buildHeader
import io.nekohasekai.sagernet.fmt.parseBoxOutbound
import io.nekohasekai.sagernet.fmt.parseBoxTLS
import io.nekohasekai.sagernet.fmt.parseBoxUot
import io.nekohasekai.sagernet.fmt.parseHeader
import io.nekohasekai.sagernet.ktx.JSONMap
import io.nekohasekai.sagernet.ktx.blankAsNull
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.map
import io.nekohasekai.sagernet.ktx.queryParameterNotBlank
import io.nekohasekai.sagernet.ktx.toStringPretty
import io.nekohasekai.sagernet.ktx.unUrlSafe
import io.nekohasekai.sagernet.ktx.wrapIPV6Host
import libcore.Libcore
import org.json.JSONObject

fun parseNaive(link: String): NaiveBean {
    val url = Libcore.parseURL(link)
    return NaiveBean().also {
        it.proto = url.scheme.substringAfter("+").substringBefore(":")
    }.apply {
        serverAddress = url.host
        serverPort = url.ports.toIntOrNull() ?: 443
        username = url.username
        password = url.password
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
            builder.fragment = name
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
        // https://github.com/klzgrad/naiveproxy/releases/tag/v130.0.6723.40-2
        // "The comma is used for delimiting proxies in a proxy chain. It must be percent-encoded in other URL components."
        put("proxy", toUri(true).replace(",", "%2C"))
        if (extraHeaders.isNotBlank()) {
            put("extra-headers", extraHeaders.split("\n").joinToString("\r\n"))
        }
        if (DataStore.logLevel > 0) {
            put("log", "")
        }
        if (insecureConcurrency > 0) {
            put("insecure-concurrency", insecureConcurrency)
        }
        put("no-post-quantum", noPostQuantum)
    }.toStringPretty()
}

fun buildSingBoxOutboundNaiveBean(bean: NaiveBean): SingBoxOptions.Outbound_NaiveOptions {
    return SingBoxOptions.Outbound_NaiveOptions().apply {
        type = bean.outboundType()
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        extra_headers = bean.extraHeaders.blankAsNull()?.let(::buildHeader)
        bean.insecureConcurrency.takeIf { it > 0 }?.let {
            insecure_concurrency = it
        }
        tls = SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            server_name = bean.sni
        }
    }
}

fun parseNaiveOutbound(json: JSONMap): NaiveBean = NaiveBean().apply {
    parseBoxOutbound(json) { key, value ->
        when (key) {
            "username" -> username = value.toString()
            "password" -> password = value.toString()
            "insecure_concurrency" -> insecureConcurrency = value.toString().toIntOrNull()
            "udp_over_tcp" -> udpOverTcp = parseBoxUot(value)

            "extra_headers" -> (value as? JSONObject)?.map?.let(::parseHeader)?.let {
                extraHeaders = it.mapNotNull { entry ->
                    entry.value.firstOrNull()?.let { value ->
                        entry.key + ":" + value
                    }
                }.joinToString("\n")
            }

            "tls" -> (value as? JSONObject)?.map?.let(::parseBoxTLS)?.let {
                sni = it.server_name
            }
        }
    }
}