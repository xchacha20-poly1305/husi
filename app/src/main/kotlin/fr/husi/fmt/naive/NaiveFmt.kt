package fr.husi.fmt.naive

import fr.husi.database.DataStore
import fr.husi.fmt.LOCALHOST4
import fr.husi.fmt.SingBoxOptions
import fr.husi.fmt.buildHeader
import fr.husi.fmt.parseBoxOutbound
import fr.husi.fmt.parseBoxTLS
import fr.husi.fmt.parseBoxUot
import fr.husi.fmt.parseHeader
import fr.husi.ktx.JSONMap
import fr.husi.ktx.blankAsNull
import fr.husi.ktx.isIpAddress
import fr.husi.ktx.map
import fr.husi.ktx.queryParameterNotBlank
import fr.husi.ktx.toStringPretty
import fr.husi.ktx.unUrlSafe
import fr.husi.ktx.wrapIPV6Host
import fr.husi.libcore.Libcore
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
        sni = url.queryParameter("sni")
        extraHeaders = url.queryParameterNotBlank("extra-headers")
            ?.unUrlSafe()
            ?.replace("\r\n", "\n")
            .orEmpty()
        insecureConcurrency = url.queryParameterNotBlank("insecure-concurrency")?.toIntOrNull() ?: 0
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
        type = SingBoxOptions.TYPE_NAIVE
        server = bean.serverAddress
        server_port = bean.serverPort
        username = bean.username
        password = bean.password
        if (bean.proto == "quic") {
            quic = true
            quic_congestion_control = bean.quicCongestionControl.blankAsNull()
        }
        extra_headers = bean.extraHeaders.blankAsNull()?.let(::buildHeader)?.toMutableMap()
        bean.insecureConcurrency.takeIf { it > 0 }?.let {
            insecure_concurrency = it
        }
        tls = SingBoxOptions.OutboundTLSOptions().apply {
            enabled = true
            server_name = bean.sni

            if (bean.enableEch) SingBoxOptions.OutboundECHOptions().apply {
                enabled = true
                config = bean.echConfig.blankAsNull()?.lines()?.toMutableList()
                query_server_name = bean.echQueryServerName.blankAsNull()
            }
        }
    }
}

fun parseNaiveOutbound(json: JSONMap): NaiveBean = NaiveBean().apply {
    parseBoxOutbound(json) { key, value ->
        when (key) {
            "username" -> username = value.toString()
            "password" -> password = value.toString()
            "insecure_concurrency" -> value.toString().toIntOrNull()?.let {
                insecureConcurrency = it
            }

            "udp_over_tcp" -> udpOverTcp = parseBoxUot(value)
            "quic" -> if (value.toString().toBoolean()) proto = "quic"
            "quic_congestion_control" -> quicCongestionControl = value.toString()

            "extra_headers" -> (value as? JSONObject)?.map?.let(::parseHeader)?.let {
                extraHeaders = it.mapNotNull { entry ->
                    entry.value.firstOrNull()?.let { value ->
                        entry.key + ":" + value
                    }
                }.joinToString("\n")
            }

            "tls" -> (value as? JSONObject)?.map?.let(::parseBoxTLS)?.let { tlsField ->
                sni = tlsField.server_name.orEmpty()
                tlsField.ech?.let { echField ->
                    enableEch = echField.enabled == true
                    echConfig = echField.config?.joinToString("\n").orEmpty()
                    echQueryServerName = echField.query_server_name.orEmpty()
                }
            }
        }
    }
}
